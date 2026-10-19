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

package uk.gov.hmrc.rdscandeproxy.euvat.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.rdscandeproxy.euvat.models.PurchaseImport
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.*
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.*
import uk.gov.hmrc.rdscandeproxy.euvat.repositories.EuVatCandeRepository

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

class EuVatServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with IntegrationPatience {

  implicit val ec: ExecutionContext = global
  private val mockConnector = mock[EuVatCandeRepository]
  private val service = new EuVatService(mockConnector)

  "EuVatService.getLatestApplications" should {
    val sampleRequest: LatestApplicationRequest = LatestApplicationRequest(
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

    val sampleResponse: LatestApplicationResponse = LatestApplicationResponse(
      applications     = List.empty,
      totalApplication = 0
    )

    "succeed" when:
      "retrieving latest applications" in:
        when(mockConnector.getLatestApplications(any()))
          .thenReturn(Future.successful(sampleResponse))
        val result = service.getLatestApplications(sampleRequest).futureValue
        result shouldBe sampleResponse

    "fail" when:
      "retrieving latest applications" in:
        when(mockConnector.getLatestApplications(any()))
          .thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.getLatestApplications(sampleRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.addApplication" should {
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

    val applicationResponse: ApplicationResponse = ApplicationResponse(1, "GB", 1)

    "succeed" when:
      "add application to the database" in:
        when(mockConnector.addApplication(any(), any()))
          .thenReturn(Future.successful(applicationResponse))
        val result = service.addApplication(appRequest, "123456789").futureValue
        result shouldBe applicationResponse

    "fail" when:
      "while saving to database" in:
        when(mockConnector.addApplication(any(), any()))
          .thenReturn(Future.failed(new Exception("bang")))

        val result = intercept[Exception](service.addApplication(appRequest, "123456789").futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.addPurchase" should {
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

    "succeed" when:
      "adding a purchase to the database" in:
        when(mockConnector.addPurchase(any()))
          .thenReturn(Future.successful(purchaseResponse))
        val result = service.addPurchase(purchaseRequest).futureValue
        result shouldBe purchaseResponse

    "fail" when:
      "saving to the database" in:
        when(mockConnector.addPurchase(any()))
          .thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.addPurchase(purchaseRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.addImport" should {
    val importRequest: AddImportRequest = AddImportRequest(
      applicationId            = 123456,
      goodsDescriptionCategory = "3",
      goodsDescriptionText     = Some("Electronics"),
      updateSequenceNumber     = 1
    )

    val importResponse: AddImportResponse = AddImportResponse(itemNumber = 4, updateSequenceNumber = 1)

    "succeed" when:
      "adding an import to the database" in:
        when(mockConnector.addImportation(any())).thenReturn(Future.successful(importResponse))
        val result = service.addImport(importRequest).futureValue
        result shouldBe importResponse

    "fail" when:
      "saving to the database" in:
        when(mockConnector.addImportation(any())).thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.addImport(importRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.getPurchaseDetails" should {
    val detailsRequest: GetPurchaseDetailsRequest = GetPurchaseDetailsRequest(applicationId = 123456, itemNumber = 4)

    val detailsResponse: GetPurchaseDetailsResponse = GetPurchaseDetailsResponse(
      goodsDescriptionCode       = "1",
      goodsDescriptionSubCode    = None,
      goodsDescriptionText       = Some("Fuel"),
      simplifiedInvoiceIndicator = None,
      supplierName               = None,
      supplierAddressLine1       = None,
      supplierAddressLine2       = None,
      supplierAddressLine3       = None,
      supplierVatNumber          = None,
      supplierTaxIdentifier      = None,
      invoiceDate                = None,
      invoiceNumber              = None,
      currencyCode               = None,
      taxableAmount              = None,
      vatAmount                  = None,
      deductibleVatAmount        = None,
      updateSequenceNumber       = 1
    )

    "succeed" when:
      "retrieving a purchase record" in:
        when(mockConnector.getPurchaseDetails(any()))
          .thenReturn(Future.successful(Some(detailsResponse)))
        val result = service.getPurchaseDetails(detailsRequest).futureValue
        result shouldBe Some(detailsResponse)

      "no purchase record exists" in:
        when(mockConnector.getPurchaseDetails(any()))
          .thenReturn(Future.successful(None))
        val result = service.getPurchaseDetails(detailsRequest).futureValue
        result shouldBe None

    "fail" when:
      "reading from the database" in:
        when(mockConnector.getPurchaseDetails(any()))
          .thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.getPurchaseDetails(detailsRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.getImportationDetails" should {
    val importationRequest: GetImportationDetailsRequest = GetImportationDetailsRequest(applicationId = 404, itemNumber = 4)

    val importationResponse: GetImportationDetailsResponse = GetImportationDetailsResponse(
      applicationId               = 404,
      itemNumber                  = 4,
      issuingDate                 = None,
      supplierName                = None,
      supplierAddressLine1        = None,
      supplierAddressLine2        = None,
      supplierAddressLine3        = None,
      supplierCountryCode         = None,
      goodsDescriptionCategory    = "10",
      goodsDescriptionSubcategory = None,
      goodsDescriptionText        = None,
      hasSadReferenceNumber       = None,
      sadReferenceNumber          = None,
      referenceInformation        = None,
      currencyCode                = None,
      taxableAmount               = None,
      vatAmount                   = None,
      deductibleVatAmount         = None,
      updateSequenceNumber        = 1,
      periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    "succeed" when:
      "retrieving an importation record" in:
        when(mockConnector.getImportationDetails(any()))
          .thenReturn(Future.successful(Some(importationResponse)))
        val result = service.getImportationDetails(importationRequest).futureValue
        result shouldBe Some(importationResponse)

      "no importation record exists" in:
        when(mockConnector.getImportationDetails(any()))
          .thenReturn(Future.successful(None))
        val result = service.getImportationDetails(importationRequest).futureValue
        result shouldBe None

    "fail" when:
      "reading from the database" in:
        when(mockConnector.getImportationDetails(any()))
          .thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.getImportationDetails(importationRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.getSupplierVrnCount" should {
    val sampleRequest: SupplierVrnCountRequest = SupplierVrnCountRequest(
      applicationId = 133,
      itemNumber    = 4,
      vatNumber     = "500000881",
      invoiceNumber = "a444"
    )
    val sampleResponse: SupplierVrnCountResponse = SupplierVrnCountResponse(duplicateCount = 1)

    "succeed" when:
      "retrieving supplier VRN count" in:
        when(mockConnector.getSupplierVrnCount(any()))
          .thenReturn(Future.successful(sampleResponse))
        val result = service.getSupplierVrnCount(sampleRequest).futureValue
        result shouldBe sampleResponse

    "fail" when:
      "retrieving supplier VRN count" in:
        when(mockConnector.getSupplierVrnCount(any()))
          .thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.getSupplierVrnCount(sampleRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.getSupplierTaxIdentifierDuplicateCount" should {
    val req = SupplierTaxIdentifierCountRequest(
      applicationId = 133,
      itemNumber    = 4,
      taxIdentifier = "500000881",
      invoiceNumber = "a444"
    )

    "succeed" in {
      when(mockConnector.getSupplierTaxIdentifierDuplicateCount(any())).thenReturn(Future.successful(4))
      val result = service.getSupplierTaxIdentifierDuplicateCount(req).futureValue
      result shouldBe 4
    }

    "fail" in {
      when(mockConnector.getSupplierTaxIdentifierDuplicateCount(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.getSupplierTaxIdentifierDuplicateCount(req).futureValue)
      result.getMessage should include("bang")
    }
  }

  "EuVatService.updatePurchaseDetails" should {
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

    "succeed" in {
      when(mockConnector.updatePurchaseDetails(any())).thenReturn(Future.successful(2))
      val result = service.updatePurchaseDetails(req).futureValue
      result shouldBe UpdatePurchaseDetailsResponse(2)
    }

    "fail" in {
      when(mockConnector.updatePurchaseDetails(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.updatePurchaseDetails(req).futureValue)
      result.getMessage should include("bang")
    }
  }

  "EuVatService.getPurchaseImportList" should {
    val req = PurchaseImportListRequest(applicationId = 133)
    val purchaseImport = PurchaseImport(123, "2", Some("2.3"), "EU", BigDecimal(300), BigDecimal(200), BigDecimal(100), "P")

    "succeed" in {
      when(mockConnector.getPurchaseImportList(any())).thenReturn(Future.successful(PurchaseImportListResponse(List(purchaseImport), 1, 100)))
      val result = service.getPurchaseImportList(req).futureValue
      result shouldBe PurchaseImportListResponse(List(purchaseImport), 1, 100)
    }

    "fail" in {
      when(mockConnector.getPurchaseImportList(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.getPurchaseImportList(req).futureValue)
      result.getMessage should include("bang")
    }
  }

  "EuVatService.deleteApplication" should {
    val req = DeleteApplicationRequest(applicationId = 999, updateSequenceNumber = 4)

    "succeed" in {
      when(mockConnector.deleteApplication(any())).thenReturn(Future.successful(()))
      service.deleteApplication(req).futureValue
    }

    "fail" in {
      when(mockConnector.deleteApplication(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.deleteApplication(req).futureValue)
      result.getMessage should include("bang")
    }
  }

  "EuVatService.updateApplicationDetails" should {
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

    "succeed" in {
      when(mockConnector.updateApplicationDetails(any())).thenReturn(Future.successful(32))
      val result = service.updateApplicationDetails(req).futureValue
      result shouldBe UpdateApplicationDetailsResponse(32)
    }

    "fail" in {
      when(mockConnector.updateApplicationDetails(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.updateApplicationDetails(req).futureValue)
      result.getMessage should include("bang")
    }
  }

  "EuVatService.deletePurchase" should {
    val req = DeletePurchaseRequest(applicationId = 404, itemNumber = 4, updateSequenceNumber = 1)

    "succeed" in {
      when(mockConnector.deletePurchase(any())).thenReturn(Future.successful(2))
      val result = service.deletePurchase(req).futureValue
      result shouldBe DeletePurchaseResponse(2)
    }

    "fail" in {
      when(mockConnector.deletePurchase(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.deletePurchase(req).futureValue)
      result.getMessage should include("bang")
    }
  }

  "EuVatService.getSadReferenceCount" should {
    val sampleRequest: SadReferenceCountRequest = SadReferenceCountRequest(
      applicationId      = 133,
      itemNumber         = 4,
      sadReferenceNumber = "500000881"
    )
    val sampleResponse: SadReferenceCountResponse = SadReferenceCountResponse(duplicateCount = 1)

    "succeed" when:
      "retrieving SAD reference count" in:
        when(mockConnector.getSadReferenceCount(any()))
          .thenReturn(Future.successful(sampleResponse))
        val result = service.getSadReferenceCount(sampleRequest).futureValue
        result shouldBe sampleResponse

    "fail" when:
      "retrieving SAD reference count" in:
        when(mockConnector.getSadReferenceCount(any()))
          .thenReturn(Future.failed(new Exception("bang")))
        val result = intercept[Exception](service.getSadReferenceCount(sampleRequest).futureValue)
        result.getMessage should include("bang")
  }

  "EuVatService.deleteImport" should {
    val req = DeleteImportRequest(applicationId = 404L, itemNumber = 4, updateSequenceNumber = 1)

    "succeed" in {
      when(mockConnector.deleteImport(any())).thenReturn(Future.successful(2))
      val result = service.deleteImport(req).futureValue
      result shouldBe DeleteImportResponse(2)
    }

    "fail" in {
      when(mockConnector.deleteImport(any())).thenReturn(Future.failed(new Exception("bang")))
      val result = intercept[Exception](service.deleteImport(req).futureValue)
      result.getMessage should include("bang")
    }
  }

}
