/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.rdscandeproxy.euvat.repositories

import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.{inOrder as mockInOrder, mock, never, times, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.db.Database
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.*
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.*

import java.sql.{CallableStatement, Connection, ResultSet}
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class EuVatCandeRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfter {

  var db: Database = _
  var repository: EuVatCandeRepository = _
  var mockConnection: Connection = _
  var mockCallableStatement: CallableStatement = _
  var mockResultSet: ResultSet = _

  before {
    db                    = mock(classOf[Database])
    mockConnection        = mock(classOf[Connection])
    mockCallableStatement = mock(classOf[CallableStatement])
    mockResultSet         = mock(classOf[ResultSet])

    when(db.withConnection(any())).thenAnswer { invocation =>
      val func = invocation.getArgument(0, classOf[Connection => Any])
      func(mockConnection) // Return the result of the lambda function passed to withConnection
    }

    when(db.withTransaction(any())).thenAnswer { invocation =>
      val func = invocation.getArgument(0, classOf[Connection => Any])
      try {
        val result = func(mockConnection)
        mockConnection.commit()
        result
      } catch {
        case ex: Throwable =>
          try mockConnection.rollback()
          catch {
            case _: Throwable => ()
          }
          throw ex
      }
    }

    when(mockConnection.prepareCall(any())).thenReturn(mockCallableStatement)
    repository = new EuVatCandeRepository(db)
  }

  "getLatestApplications" should {
    "return a LatestApplicationResponse with correct data" in {
      val request = LatestApplicationRequest(
        applicantVatRegNumber = "123456789",
        refundingCountry      = Some("LV"),
        startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
        endDate               = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
        representativeId      = Some("rep123"),
        maxNumber             = 10,
        orderBy               = None,
        sortOrder             = None,
        startAt               = None
      )

      when(mockCallableStatement.getObject("p_applications", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_total_applications")).thenReturn(1)

      when(mockResultSet.next()).thenReturn(true, false)
      when(mockResultSet.getLong("application_id")).thenReturn(133L)
      when(mockResultSet.getString("refunding_country_code")).thenReturn("LV")
      when(mockResultSet.getTimestamp("period_start_date")).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 2, 1, 0, 0)))
      when(mockResultSet.getTimestamp("period_end_date")).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 5, 31, 23, 59)))
      when(mockResultSet.getString("application_number")).thenReturn("GB0000000000000133")
      when(mockResultSet.getString("application_status")).thenReturn("D")
      when(mockResultSet.getString("submission_status")).thenReturn("S")
      when(mockResultSet.getTimestamp("application_version")).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 2, 11, 10, 38)))

      val result = repository.getLatestApplications(request).futureValue

      result.totalApplication                       shouldBe 1
      result.applications.head.applicationId        shouldBe 133L
      result.applications.head.refundingCountryCode shouldBe "LV"
    }

    "return empty list when no applications found" in {
      val request = LatestApplicationRequest(
        applicantVatRegNumber = "123456789",
        refundingCountry      = Some("LV"),
        startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
        endDate               = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
        representativeId      = Some("rep123"),
        maxNumber             = 10,
        orderBy               = None,
        sortOrder             = None,
        startAt               = None
      )

      when(mockCallableStatement.getObject("p_applications", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_total_applications")).thenReturn(0)
      when(mockResultSet.next()).thenReturn(false)

      val result = repository.getLatestApplications(request).futureValue

      result.totalApplication shouldBe 0
      result.applications     shouldBe List.empty
    }

    "set null params when optional fields are absent" in {
      val request = LatestApplicationRequest(
        applicantVatRegNumber = "123456789",
        refundingCountry      = None,
        startDate             = None,
        endDate               = None,
        representativeId      = None,
        maxNumber             = 10,
        orderBy               = None,
        sortOrder             = None,
        startAt               = None
      )

      when(mockCallableStatement.getObject("p_applications", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_total_applications")).thenReturn(0)
      when(mockResultSet.next()).thenReturn(false)

      repository.getLatestApplications(request).futureValue

      verify(mockCallableStatement).setNull("p_refunding_country", java.sql.Types.VARCHAR)
      verify(mockCallableStatement).setNull("p_start_date", java.sql.Types.DATE)
      verify(mockCallableStatement).setNull("p_end_date", java.sql.Types.DATE)
      verify(mockCallableStatement).setNull("p_representative_id", java.sql.Types.VARCHAR)
    }
  }

  "addApplication" should {
    "return saved application response" in {
      val appRequest: ApplicationRequest = ApplicationRequest(
        refundingCountryCode          = Some("FR"),
        periodStartDate               = Some(LocalDateTime.of(2025, 1, 1, 0, 0, 0)),
        periodEndDate                 = Some(LocalDateTime.of(2025, 3, 31, 23, 59, 59)),
        applicantEmailAddress         = Some("test@email.com"),
        applicantTelephoneNumber      = Some("0123456789"),
        applicationLanguage           = Some("EN"),
        businessActivityCode1         = Some("7090"),
        businessActivityCode2         = Some("8903"),
        businessActivityCode3         = None,
        representativeId              = None,
        representativeCountryCode     = None,
        representativeEmailAddress    = None,
        representativeIdType          = None,
        representativeTelephoneNumber = None,
        bankAccountOwnerName          = None,
        bankAccountOwnerType          = None,
        iBanCode                      = None,
        bicCode                       = None,
        bankAccountCurrencyCode       = None
      )

      val applicationResponse: ApplicationResponse = ApplicationResponse(1, "GB123456", 1)

      when(mockCallableStatement.getInt("p_application_id")).thenReturn(1)
      when(mockCallableStatement.getString("p_application_number")).thenReturn("GB123456")
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(1)

      val result = await(repository.addApplication(appRequest, "123456789"))
      result shouldBe applicationResponse
    }
  }

  "addPurchase" should {
    "return purchase response" in {
      val purchaseRequest: AddPurchaseRequest = AddPurchaseRequest(
        applicationId              = 123456,
        goodsDescriptionCategory   = "1",
        goodsDescriptionText       = Some("Fuel"),
        purchaseSubcategory        = None,
        simplifiedInvoiceIndicator = None,
        supplierName               = None,
        supplierAddress1           = None,
        supplierAddress2           = None,
        supplierAddress3           = None,
        supplierVatRegNumber       = None,
        supplierTaxIdentifier      = None,
        invoiceDate                = None,
        invoiceNumber              = None,
        currencyCode               = None,
        taxableAmount              = None,
        vatAmount                  = None,
        deductibleVatAmount        = None,
        updateSequenceNumber       = 1
      )

      val purchaseResponse: AddPurchaseResponse = AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)

      when(mockCallableStatement.getInt("p_item_number")).thenReturn(4)
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(1)

      val result = await(repository.addPurchase(purchaseRequest))
      result shouldBe purchaseResponse
    }
  }

  "addImportation" should {
    "return import response" in {
      val importRequest: AddImportRequest = AddImportRequest(
        applicationId            = 123456,
        goodsDescriptionCategory = "3",
        goodsDescriptionText     = Some("Electronics"),
        updateSequenceNumber     = 1
      )

      val importResponse: AddImportResponse = AddImportResponse(itemNumber = 4, updateSequenceNumber = 1)

      when(mockCallableStatement.getInt("p_item_number")).thenReturn(4)
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(1)

      val result = await(repository.addImportation(importRequest))

      result shouldBe importResponse
    }
  }

  "getPurchaseDetails" should {
    "return the purchase record with the update sequence number" in {
      val request = GetPurchaseDetailsRequest(applicationId = 123456, itemNumber = 4)

      when(mockCallableStatement.getObject("p_purchase_details", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(7)

      when(mockResultSet.next()).thenReturn(true, false)
      when(mockResultSet.getString("goods_description_category")).thenReturn("1")
      when(mockResultSet.getString("goods_description_subcategory")).thenReturn("1.1")
      when(mockResultSet.getString("goods_description_text")).thenReturn("Fuel")
      when(mockResultSet.getString("simplified_invoice_indicator")).thenReturn(null)
      when(mockResultSet.getString("supplier_name")).thenReturn("Supplier Ltd")
      when(mockResultSet.getString("supplier_address_1")).thenReturn("1 High Street")
      when(mockResultSet.getString("supplier_address_2")).thenReturn(null)
      when(mockResultSet.getString("supplier_address_3")).thenReturn(null)
      when(mockResultSet.getString("supplier_vat_reg_number")).thenReturn("LV40003567907")
      when(mockResultSet.getString("supplier_tax_identifier")).thenReturn(null)
      when(mockResultSet.getTimestamp("invoice_date")).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 3, 15, 0, 0)))
      when(mockResultSet.getString("invoice_number")).thenReturn("INV-001")
      when(mockResultSet.getString("currency_code")).thenReturn("EUR")
      when(mockResultSet.getBigDecimal("taxable_amount")).thenReturn(new java.math.BigDecimal("100.50"))
      when(mockResultSet.getBigDecimal("vat_amount")).thenReturn(new java.math.BigDecimal("21.10"))
      when(mockResultSet.getBigDecimal("deductible_vat_amount")).thenReturn(null)

      val result = repository.getPurchaseDetails(request).futureValue

      result shouldBe Some(
        GetPurchaseDetailsResponse(
          goodsDescriptionCode       = "1",
          goodsDescriptionSubCode    = Some("1.1"),
          goodsDescriptionText       = Some("Fuel"),
          simplifiedInvoiceIndicator = None,
          supplierName               = Some("Supplier Ltd"),
          supplierAddressLine1       = Some("1 High Street"),
          supplierAddressLine2       = None,
          supplierAddressLine3       = None,
          supplierVatNumber          = Some("LV40003567907"),
          supplierTaxIdentifier      = None,
          invoiceDate                = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
          invoiceNumber              = Some("INV-001"),
          currencyCode               = Some("EUR"),
          taxableAmount              = Some(BigDecimal("100.50")),
          vatAmount                  = Some(BigDecimal("21.10")),
          deductibleVatAmount        = None,
          updateSequenceNumber       = 7
        )
      )
    }

    "return None when the cursor holds no purchase record" in {
      val request = GetPurchaseDetailsRequest(applicationId = 123456, itemNumber = 99)

      when(mockCallableStatement.getObject("p_purchase_details", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(7)
      when(mockResultSet.next()).thenReturn(false)

      val result = repository.getPurchaseDetails(request).futureValue
      result shouldBe None
    }
  }

  "getImportationDetails" should {
    "return the importation record with the update sequence number" in {
      val request = GetImportationDetailsRequest(applicationId = 404, itemNumber = 4)

      when(mockCallableStatement.getObject("p_importation_details", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(1)
      when(mockCallableStatement.getDate("p_period_start_date")).thenReturn(java.sql.Date.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0).toLocalDate))

      when(mockResultSet.next()).thenReturn(true, false)
      when(mockResultSet.getLong("application_id")).thenReturn(404L)
      when(mockResultSet.getInt("item_number")).thenReturn(4)
      when(mockResultSet.getTimestamp("issuing_date")).thenReturn(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 5, 14, 0, 0)))
      when(mockResultSet.getString("supplier_name")).thenReturn("Finnish International")
      when(mockResultSet.getString("supplier_address_1")).thenReturn("356 High Street")
      when(mockResultSet.getString("supplier_address_2")).thenReturn("Rochdale")
      when(mockResultSet.getString("supplier_address_3")).thenReturn("England")
      when(mockResultSet.getString("supplier_country_code")).thenReturn("GB")
      when(mockResultSet.getString("goods_description_category")).thenReturn("10")
      when(mockResultSet.getString("goods_description_subcategory")).thenReturn("10.4.1")
      when(mockResultSet.getString("goods_description_text")).thenReturn("")
      when(mockResultSet.getString("has_sad_reference_number")).thenReturn("Y")
      when(mockResultSet.getString("sad_reference_number")).thenReturn("SAD123456789")
      when(mockResultSet.getString("reference_information")).thenReturn("Import declaration reference")
      when(mockResultSet.getString("currency_code")).thenReturn("EUR")
      when(mockResultSet.getBigDecimal("taxable_amount")).thenReturn(new java.math.BigDecimal("1000"))
      when(mockResultSet.getBigDecimal("vat_amount")).thenReturn(new java.math.BigDecimal("99"))
      when(mockResultSet.getBigDecimal("deductible_vat_amount")).thenReturn(new java.math.BigDecimal("40"))

      val result = repository.getImportationDetails(request).futureValue

      result shouldBe Some(
        GetImportationDetailsResponse(
          applicationId               = 404,
          itemNumber                  = 4,
          issuingDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
          supplierName                = Some("Finnish International"),
          supplierAddressLine1        = Some("356 High Street"),
          supplierAddressLine2        = Some("Rochdale"),
          supplierAddressLine3        = Some("England"),
          supplierCountryCode         = Some("GB"),
          goodsDescriptionCategory    = "10",
          goodsDescriptionSubcategory = Some("10.4.1"),
          goodsDescriptionText        = Some(""),
          hasSadReferenceNumber       = Some("Y"),
          sadReferenceNumber          = Some("SAD123456789"),
          referenceInformation        = Some("Import declaration reference"),
          currencyCode                = Some("EUR"),
          taxableAmount               = Some(BigDecimal("1000")),
          vatAmount                   = Some(BigDecimal("99")),
          deductibleVatAmount         = Some(BigDecimal("40")),
          updateSequenceNumber        = 1,
          periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
        )
      )
    }

    "return None when the cursor holds no importation record" in {
      val request = GetImportationDetailsRequest(applicationId = 404, itemNumber = 99)

      when(mockCallableStatement.getObject("p_importation_details", classOf[ResultSet])).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(1)
      when(mockCallableStatement.getDate("p_period_start_date")).thenReturn(java.sql.Date.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0).toLocalDate))
      when(mockResultSet.next()).thenReturn(false)

      val result = repository.getImportationDetails(request).futureValue
      result shouldBe None
    }
  }

  "getSupplierVrnCount" should {
    "return a SupplierVrnCountResponse with the duplicate count" in {
      val request = SupplierVrnCountRequest(
        applicationId = 133,
        itemNumber    = 4,
        vatNumber     = "500000881",
        invoiceNumber = "a444"
      )

      when(mockCallableStatement.getInt("p_count")).thenReturn(3)
      val result = repository.getSupplierVrnCount(request).futureValue
      result.duplicateCount shouldBe 3
    }

    "return zero when no duplicates exist" in {
      val request = SupplierVrnCountRequest(
        applicationId = 133,
        itemNumber    = 4,
        vatNumber     = "500000881",
        invoiceNumber = "a444"
      )

      when(mockCallableStatement.getInt("p_count")).thenReturn(0)
      val result = repository.getSupplierVrnCount(request).futureValue
      result.duplicateCount shouldBe 0
    }
  }

  "getSupplierTaxIdentifierDuplicateCount" should {
    "return the duplicate count from proc" in {
      val req = SupplierTaxIdentifierCountRequest(
        applicationId = 133,
        itemNumber    = 4,
        taxIdentifier = "500000881",
        invoiceNumber = "a444"
      )

      when(mockCallableStatement.getInt("p_count")).thenReturn(4)
      val result = repository.getSupplierTaxIdentifierDuplicateCount(req).futureValue
      result shouldBe 4
    }
  }

  "updatePurchaseDetails" should {
    "return the updated sequence number from proc" in {
      val req = uk.gov.hmrc.rdscandeproxy.euvat.models.requests.UpdatePurchaseDetailsRequest(
        applicationId               = 404,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "10",
        goodsDescriptionSubCategory = Some("10.4.1"),
        goodsDescriptionText        = Some("office stationery and consumables"),
        simplifiedInvoiceIndicator  = Some("N"),
        supplierName                = Some("Finnish International"),
        supplierAddress1            = Some("356 High Street"),
        supplierAddress2            = Some("Rochdale"),
        supplierAddress3            = Some("England"),
        supplierVatRegNumber        = Some("500000881"),
        supplierTaxIdentifier       = Some(""),
        invoiceDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
        invoiceNumber               = Some("a444"),
        currencyCode                = Some("EUR"),
        taxableAmount               = Some(BigDecimal(1000)),
        vatAmount                   = Some(BigDecimal(99)),
        deductibleVatAmount         = Some(BigDecimal(40)),
        updateSequenceNumber        = 1
      )

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(2)
      val result = repository.updatePurchaseDetails(req).futureValue
      result shouldBe 2
    }

    "use a single DB connection and call prepareCall for each SP" in {
      val req = uk.gov.hmrc.rdscandeproxy.euvat.models.requests.UpdatePurchaseDetailsRequest(
        applicationId               = 404,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "10",
        goodsDescriptionSubCategory = Some("10.4.1"),
        goodsDescriptionText        = Some("office stationery and consumables"),
        simplifiedInvoiceIndicator  = Some("N"),
        supplierName                = Some("Finnish International"),
        supplierAddress1            = Some("356 High Street"),
        supplierAddress2            = Some("Rochdale"),
        supplierAddress3            = Some("England"),
        supplierVatRegNumber        = Some("500000881"),
        supplierTaxIdentifier       = Some(""),
        invoiceDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
        invoiceNumber               = Some("a444"),
        currencyCode                = Some("EUR"),
        taxableAmount               = Some(BigDecimal(1000)),
        vatAmount                   = Some(BigDecimal(99)),
        deductibleVatAmount         = Some(BigDecimal(40)),
        updateSequenceNumber        = 1
      )

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(2)

      val result = repository.updatePurchaseDetails(req).futureValue

      result shouldBe 2
      verify(mockConnection, times(4)).prepareCall(any())
      val inOrderVerifier = mockInOrder(mockConnection)
      inOrderVerifier.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseCategory(?, ?, ?, ?)}")
      inOrderVerifier.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseSubCategory(?, ?, ?, ?)}")
      inOrderVerifier.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDescription(?, ?, ?, ?)}")
      inOrderVerifier
        .verify(mockConnection)
        .prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDetails(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")
    }

    "call prepareCall twice when optional description/subcategory absent" in {
      val req = uk.gov.hmrc.rdscandeproxy.euvat.models.requests.UpdatePurchaseDetailsRequest(
        applicationId               = 404,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "10",
        goodsDescriptionSubCategory = None,
        goodsDescriptionText        = None,
        simplifiedInvoiceIndicator  = Some("N"),
        supplierName                = Some("Finnish International"),
        supplierAddress1            = Some("356 High Street"),
        supplierAddress2            = Some("Rochdale"),
        supplierAddress3            = Some("England"),
        supplierVatRegNumber        = Some("500000881"),
        supplierTaxIdentifier       = Some(""),
        invoiceDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
        invoiceNumber               = Some("a444"),
        currencyCode                = Some("EUR"),
        taxableAmount               = Some(BigDecimal(1000)),
        vatAmount                   = Some(BigDecimal(99)),
        deductibleVatAmount         = Some(BigDecimal(40)),
        updateSequenceNumber        = 1
      )

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(5)

      val result = repository.updatePurchaseDetails(req).futureValue

      result shouldBe 5
      verify(mockConnection, times(2)).prepareCall(any())
    }

    "call prepareCall three times when only description present" in {
      val req = uk.gov.hmrc.rdscandeproxy.euvat.models.requests.UpdatePurchaseDetailsRequest(
        applicationId               = 404,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "10",
        goodsDescriptionSubCategory = None,
        goodsDescriptionText        = Some("office stationery and consumables"),
        simplifiedInvoiceIndicator  = Some("N"),
        supplierName                = Some("Finnish International"),
        supplierAddress1            = Some("356 High Street"),
        supplierAddress2            = Some("Rochdale"),
        supplierAddress3            = Some("England"),
        supplierVatRegNumber        = Some("500000881"),
        supplierTaxIdentifier       = Some(""),
        invoiceDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
        invoiceNumber               = Some("a444"),
        currencyCode                = Some("EUR"),
        taxableAmount               = Some(BigDecimal(1000)),
        vatAmount                   = Some(BigDecimal(99)),
        deductibleVatAmount         = Some(BigDecimal(40)),
        updateSequenceNumber        = 1
      )

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(7)

      val result = repository.updatePurchaseDetails(req).futureValue
      result shouldBe 7

      verify(mockConnection, times(3)).prepareCall(any())
      val inOrderVerifierDesc = mockInOrder(mockConnection)
      inOrderVerifierDesc.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseCategory(?, ?, ?, ?)}")
      inOrderVerifierDesc.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDescription(?, ?, ?, ?)}")
      inOrderVerifierDesc
        .verify(mockConnection)
        .prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDetails(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")
    }

    "call prepareCall three times when only subcategory present" in {
      val req = uk.gov.hmrc.rdscandeproxy.euvat.models.requests.UpdatePurchaseDetailsRequest(
        applicationId               = 404,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "10",
        goodsDescriptionSubCategory = Some("10.4.1"),
        goodsDescriptionText        = None,
        simplifiedInvoiceIndicator  = Some("N"),
        supplierName                = Some("Finnish International"),
        supplierAddress1            = Some("356 High Street"),
        supplierAddress2            = Some("Rochdale"),
        supplierAddress3            = Some("England"),
        supplierVatRegNumber        = Some("500000881"),
        supplierTaxIdentifier       = Some(""),
        invoiceDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
        invoiceNumber               = Some("a444"),
        currencyCode                = Some("EUR"),
        taxableAmount               = Some(BigDecimal(1000)),
        vatAmount                   = Some(BigDecimal(99)),
        deductibleVatAmount         = Some(BigDecimal(40)),
        updateSequenceNumber        = 1
      )

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(9)

      val result = repository.updatePurchaseDetails(req).futureValue
      result shouldBe 9

      verify(mockConnection, times(3)).prepareCall(any())
      val inOrderVerifierSub = mockInOrder(mockConnection)
      inOrderVerifierSub.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseCategory(?, ?, ?, ?)}")
      inOrderVerifierSub.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseSubCategory(?, ?, ?, ?)}")
      inOrderVerifierSub
        .verify(mockConnection)
        .prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDetails(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")
    }

    "rollback the transaction and propagate exception when an intermediate SP fails" in {
      val req = UpdatePurchaseDetailsRequest(
        applicationId               = 404,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "10",
        goodsDescriptionSubCategory = Some("10.4.1"),
        goodsDescriptionText        = Some("office stationery and consumables"),
        simplifiedInvoiceIndicator  = Some("N"),
        supplierName                = Some("Finnish International"),
        supplierAddress1            = Some("356 High Street"),
        supplierAddress2            = Some("Rochdale"),
        supplierAddress3            = Some("England"),
        supplierVatRegNumber        = Some("500000881"),
        supplierTaxIdentifier       = Some(""),
        invoiceDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
        invoiceNumber               = Some("a444"),
        currencyCode                = Some("EUR"),
        taxableAmount               = Some(BigDecimal(1000)),
        vatAmount                   = Some(BigDecimal(99)),
        deductibleVatAmount         = Some(BigDecimal(40)),
        updateSequenceNumber        = 1
      )

      val cs1 = mock(classOf[CallableStatement])
      val cs2 = mock(classOf[CallableStatement])
      val cs3 = mock(classOf[CallableStatement])
      val cs4 = mock(classOf[CallableStatement])

      when(mockConnection.prepareCall(any())).thenReturn(cs1, cs2, cs3, cs4)

      when(cs1.getInt("p_update_seq_number")).thenReturn(2)
      when(cs2.execute()).thenThrow(new java.sql.SQLException("SP failure"))

      val thrown = intercept[Exception] {
        repository.updatePurchaseDetails(req).futureValue
      }

      thrown.getMessage should include("SP failure")

      verify(mockConnection).rollback()
      verify(mockConnection, never()).commit()
    }
  }

  "getPurchaseImportList" should {
    "return a PurchaseImportListResponse with correct data" in {
      val request = PurchaseImportListRequest(
        applicationId = 3456789,
        maxNumber     = Some(10),
        orderBy       = 2,
        sortOrder     = "DESC",
        startAt       = 1
      )

      when(mockCallableStatement.getObject("p_purch_and_imp_list")).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_total_items")).thenReturn(1)
      when(mockCallableStatement.getBigDecimal("p_total_deductible_vat")).thenReturn(new java.math.BigDecimal("220"))
      when(mockCallableStatement.execute()).thenReturn(true)

      when(mockResultSet.next()).thenReturn(true, false)
      when(mockResultSet.getInt("item_number")).thenReturn(123)
      when(mockResultSet.getString("item_type")).thenReturn("P")
      when(mockResultSet.getString("goods_description_category")).thenReturn("9")
      when(mockResultSet.getString("goods_description_subcategory")).thenReturn("9.18")
      when(mockResultSet.getString("currency_code")).thenReturn("EU")
      when(mockResultSet.getBigDecimal("deductible_vat_amount")).thenReturn(new java.math.BigDecimal("220"))

      val result = repository.getPurchaseImportList(request).futureValue
      val purchaseImport = result.purchaseImportList.head
      purchaseImport.itemNumber shouldBe 123
      purchaseImport.itemType   shouldBe "P"
      result.totalVatClaims     shouldBe 220
      result.totalItems         shouldBe 1
    }

    "return empty list when no purchase or imports found" in {
      val request = PurchaseImportListRequest(
        applicationId = 805,
        maxNumber     = Some(10),
        orderBy       = 3,
        sortOrder     = "DESC",
        startAt       = 2
      )

      when(mockCallableStatement.getObject("p_purch_and_imp_list")).thenReturn(mockResultSet)
      when(mockCallableStatement.getInt("p_total_items")).thenReturn(0)
      when(mockResultSet.next()).thenReturn(false)

      val result = repository.getPurchaseImportList(request).futureValue

      result.totalItems         shouldBe 0
      result.purchaseImportList shouldBe List.empty
    }
  }

  "deleteApplication" should {
    "call the deleteApplication stored procedure with correct params" in {
      val req = DeleteApplicationRequest(applicationId = 555, updateSequenceNumber = 2)

      await(repository.deleteApplication(req))

      verify(mockConnection).prepareCall(any())
      verify(mockCallableStatement).setInt("p_application_id", 555)
      verify(mockCallableStatement).setInt("p_update_seq_number", 2)
      verify(mockCallableStatement).execute()
    }

    "propagate exception when stored procedure fails" in {
      val req = DeleteApplicationRequest(applicationId = 777, updateSequenceNumber = 3)

      when(mockCallableStatement.execute()).thenThrow(new RuntimeException("SP failure"))

      intercept[RuntimeException] {
        await(repository.deleteApplication(req))
      }
    }
  }

  "deleteImport" should {
    "call the deleteImportationDetails stored procedure with correct params" in {
      val req = DeleteImportRequest(applicationId = 404L, itemNumber = 4, updateSequenceNumber = 1)

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(2)

      val result = repository.deleteImport(req).futureValue
      result shouldBe 2

      verify(mockConnection).prepareCall(any())
      verify(mockCallableStatement).setLong("p_application_id", 404L)
      verify(mockCallableStatement).setInt("p_item_number", 4)
      verify(mockCallableStatement).setInt("p_update_seq_number", 1)
      verify(mockCallableStatement).execute()
    }

    "propagate exception when stored procedure fails" in {
      val req = DeleteImportRequest(applicationId = 999L, itemNumber = 5, updateSequenceNumber = 3)

      when(mockCallableStatement.execute()).thenThrow(new RuntimeException("SP failure"))

      intercept[RuntimeException] {
        repository.deleteImport(req).futureValue
      }
    }
  }

  "updateApplicationDetails" should {
    "call language then details, chaining the update sequence number" in {
      val req = UpdateApplicationDetailsRequest(
        applicationId              = 133,
        applicationLanguage        = "en",
        refundingCountry           = "LV",
        periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0),
        periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
        applicantEmailAddress      = "test@hotmail.com",
        applicantPhoneNumber       = Some("01952233299"),
        representativeCountry      = None,
        representativeEmailAddress = None,
        representativePhoneNumber  = None,
        bankAccountOwnerName       = None,
        bankAccountOwnerType       = None,
        ibanCode                   = None,
        bicCode                    = None,
        bankAccountCurrencyCode    = None,
        businessActivityCode2      = None,
        businessActivityCode3      = None,
        cipherText                 = None,
        encryptionStatus           = None,
        updateSequenceNumber       = 30
      )

      val cs1 = mock(classOf[CallableStatement])
      val cs2 = mock(classOf[CallableStatement])

      when(mockConnection.prepareCall(any())).thenReturn(cs1, cs2)
      when(cs1.getInt("p_update_seq_number")).thenReturn(31)
      when(cs2.getInt("p_update_seq_number")).thenReturn(32)

      val result = repository.updateApplicationDetails(req).futureValue

      result shouldBe 32
      verify(cs1).setString("p_application_language", "en")
      verify(cs1).setInt("p_update_seq_number", 30)
      verify(cs2).setInt("p_update_seq_number", 31)

      verify(mockConnection, times(2)).prepareCall(any())
      val inOrderVerifier = mockInOrder(mockConnection)
      inOrderVerifier.verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updateApplicationLanguage(?, ?, ?)}")
      inOrderVerifier
        .verify(mockConnection)
        .prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updateApplicationDetails(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")
      verify(mockConnection).commit()
    }

    "set mandatory params and pass null for absent optional fields" in {
      val req = UpdateApplicationDetailsRequest(
        applicationId              = 133,
        applicationLanguage        = "en",
        refundingCountry           = "LV",
        periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0),
        periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
        applicantEmailAddress      = "test@hotmail.com",
        applicantPhoneNumber       = Some("01952233299"),
        representativeCountry      = None,
        representativeEmailAddress = None,
        representativePhoneNumber  = None,
        bankAccountOwnerName       = None,
        bankAccountOwnerType       = None,
        ibanCode                   = None,
        bicCode                    = None,
        bankAccountCurrencyCode    = None,
        businessActivityCode2      = None,
        businessActivityCode3      = None,
        cipherText                 = None,
        encryptionStatus           = None,
        updateSequenceNumber       = 30
      )

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(31)

      repository.updateApplicationDetails(req).futureValue

      // application id is set by both SPs
      verify(mockCallableStatement, times(2)).setLong("p_application_id", 133L)
      verify(mockCallableStatement).setString("p_application_language", "en")
      verify(mockCallableStatement).setString("p_refunding_country_code", "LV")
      verify(mockCallableStatement).setString("p_applicant_email_address", "test@hotmail.com")
      verify(mockCallableStatement).setString("p_applicant_telephone_num", "01952233299")
      verify(mockCallableStatement).setString("p_iban_code", null)
      verify(mockCallableStatement).setString("p_bic_code", null)
      verify(mockCallableStatement).setString("p_cipher_text", null)
    }

    "rollback and not call details when the language SP fails" in {
      val req = UpdateApplicationDetailsRequest(
        applicationId              = 133,
        applicationLanguage        = "en",
        refundingCountry           = "LV",
        periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0),
        periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
        applicantEmailAddress      = "test@hotmail.com",
        applicantPhoneNumber       = Some("01952233299"),
        representativeCountry      = None,
        representativeEmailAddress = None,
        representativePhoneNumber  = None,
        bankAccountOwnerName       = None,
        bankAccountOwnerType       = None,
        ibanCode                   = None,
        bicCode                    = None,
        bankAccountCurrencyCode    = None,
        businessActivityCode2      = None,
        businessActivityCode3      = None,
        cipherText                 = None,
        encryptionStatus           = None,
        updateSequenceNumber       = 30
      )

      val cs1 = mock(classOf[CallableStatement])

      when(mockConnection.prepareCall(any())).thenReturn(cs1)
      when(cs1.execute()).thenThrow(new java.sql.SQLException("SP failure"))

      val thrown = intercept[Exception] {
        repository.updateApplicationDetails(req).futureValue
      }

      thrown.getMessage should include("SP failure")
      verify(mockConnection, times(1)).prepareCall(any())
      verify(mockConnection).rollback()
      verify(mockConnection, never()).commit()
    }

    "rollback the transaction and propagate exception when the details SP fails" in {
      val req = UpdateApplicationDetailsRequest(
        applicationId              = 133,
        applicationLanguage        = "en",
        refundingCountry           = "LV",
        periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0),
        periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
        applicantEmailAddress      = "test@hotmail.com",
        applicantPhoneNumber       = Some("01952233299"),
        representativeCountry      = None,
        representativeEmailAddress = None,
        representativePhoneNumber  = None,
        bankAccountOwnerName       = None,
        bankAccountOwnerType       = None,
        ibanCode                   = None,
        bicCode                    = None,
        bankAccountCurrencyCode    = None,
        businessActivityCode2      = None,
        businessActivityCode3      = None,
        cipherText                 = None,
        encryptionStatus           = None,
        updateSequenceNumber       = 30
      )

      val cs1 = mock(classOf[CallableStatement])
      val cs2 = mock(classOf[CallableStatement])

      when(mockConnection.prepareCall(any())).thenReturn(cs1, cs2)
      when(cs1.getInt("p_update_seq_number")).thenReturn(31)
      when(cs2.execute()).thenThrow(new java.sql.SQLException("SP failure"))

      val thrown = intercept[Exception] {
        repository.updateApplicationDetails(req).futureValue
      }

      thrown.getMessage should include("SP failure")
      verify(mockConnection).rollback()
      verify(mockConnection, never()).commit()
    }
  }

  "deletePurchase" should {
    "call the deletePurchaseDetails SP and return the new update sequence number" in {
      val req = DeletePurchaseRequest(applicationId = 404, itemNumber = 4, updateSequenceNumber = 1)

      when(mockCallableStatement.getInt("p_update_seq_number")).thenReturn(2)

      val result = repository.deletePurchase(req).futureValue

      result shouldBe 2
      verify(mockConnection).prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.deletePurchaseDetails(?, ?, ?)}")
      verify(mockCallableStatement).setLong("p_application_id", 404L)
      verify(mockCallableStatement).setInt("p_item_number", 4)
      verify(mockCallableStatement).setInt("p_update_seq_number", 1)
      verify(mockCallableStatement).execute()
    }

    "propagate exception when stored procedure fails" in {
      val req = DeletePurchaseRequest(applicationId = 404, itemNumber = 99, updateSequenceNumber = 1)

      when(mockCallableStatement.execute()).thenThrow(new java.sql.SQLException("SP failure"))

      val thrown = intercept[Exception] {
        repository.deletePurchase(req).futureValue
      }

      thrown.getMessage should include("SP failure")
    }
  }

  "getSadReferenceCount" should {
    "return a SadReferenceCountResponse with the duplicate count" in {
      val request = SadReferenceCountRequest(
        applicationId      = 133,
        itemNumber         = 4,
        sadReferenceNumber = "500000881"
      )

      when(mockCallableStatement.getInt("p_count")).thenReturn(3)
      val result = repository.getSadReferenceCount(request).futureValue
      result.duplicateCount shouldBe 3
    }

    "return zero when no duplicates exist" in {
      val request = SadReferenceCountRequest(
        applicationId      = 133,
        itemNumber         = 4,
        sadReferenceNumber = "500000881"
      )

      when(mockCallableStatement.getInt("p_count")).thenReturn(0)
      val result = repository.getSadReferenceCount(request).futureValue
      result.duplicateCount shouldBe 0
    }
  }
}
