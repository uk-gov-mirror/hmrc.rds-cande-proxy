/*
 * Copyright 2026 HM Revenue & Customs
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

import oracle.jdbc.OracleTypes
import play.api.Logging
import play.api.db.{Database, NamedDatabase}
import uk.gov.hmrc.rdscandeproxy.euvat.models.PurchaseImport
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.*
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.*

import java.sql.{Connection, ResultSet}
import java.time.LocalDateTime
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class EuVatCandeRepository @Inject() (@NamedDatabase("euvat") db: Database)(implicit ec: ExecutionContext) extends Logging {

  def getLatestApplications(request: LatestApplicationRequest): Future[LatestApplicationResponse] = {
    logger.info(s"Calling stored procedure getLatestApplications for VRN: ${request.applicantVatRegNumber}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getLatestApplications(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
          storedProcedure =>
            // Set input parameters
            storedProcedure.setString("p_applicant_vat_reg_number", request.applicantVatRegNumber)
            request.refundingCountry match {
              case Some(country) => storedProcedure.setString("p_refunding_country", country)
              case None          => storedProcedure.setNull("p_refunding_country", java.sql.Types.VARCHAR)
            }

            request.startDate match {
              case Some(date) => storedProcedure.setDate("p_start_date", java.sql.Date.valueOf(date.toLocalDate))
              case None       => storedProcedure.setNull("p_start_date", java.sql.Types.DATE)
            }

            request.endDate match {
              case Some(date) => storedProcedure.setDate("p_end_date", java.sql.Date.valueOf(date.toLocalDate))
              case None       => storedProcedure.setNull("p_end_date", java.sql.Types.DATE)
            }
            request.representativeId match {
              case Some(repId) => storedProcedure.setString("p_representative_id", repId)
              case None        => storedProcedure.setNull("p_representative_id", java.sql.Types.VARCHAR)
            }
            storedProcedure.setInt("p_order_by", request.orderBy.getOrElse(0))
            storedProcedure.setString("p_sort_order", request.sortOrder.orNull)
            storedProcedure.setInt("p_start_at", request.startAt.getOrElse(0))
            storedProcedure.setInt("p_max_number", request.maxNumber)

            // Register output parameters
            storedProcedure.registerOutParameter("p_applications", OracleTypes.CURSOR)
            storedProcedure.registerOutParameter("p_total_applications", OracleTypes.NUMBER)

            // Execute
            storedProcedure.execute()

            // Retrieve output parameters
            val totalApplications = storedProcedure.getInt("p_total_applications")
            val rs = storedProcedure.getObject("p_applications", classOf[ResultSet])

            Using.resource(rs) { cursor =>
              val applications =
                Iterator
                  .continually(cursor.next())
                  .takeWhile(identity)
                  .map(_ =>
                    LatestApplication(
                      applicationId        = cursor.getLong("application_id"),
                      refundingCountryCode = cursor.getString("refunding_country_code"),
                      periodStartDate      = cursor.getTimestamp("period_start_date").toLocalDateTime,
                      periodEndDate        = cursor.getTimestamp("period_end_date").toLocalDateTime,
                      applicationNumber    = cursor.getString("application_number"),
                      applicationStatus    = Option(cursor.getString("application_status")),
                      submissionStatus     = Option(cursor.getString("submission_status")),
                      applicationVersion   = cursor.getTimestamp("application_version").toLocalDateTime
                    )
                  )
                  .toList
              LatestApplicationResponse(applications, totalApplications)
            }
        }
      }
    }
  }

  def getSupplierTaxIdentifierDuplicateCount(request: SupplierTaxIdentifierCountRequest): Future[Int] = {
    logger.info(
      s"Calling stored procedure getSupplierTaxIdentifierCount for applicationId: ${request.applicationId} itemNumber: ${request.itemNumber}"
    )
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getSupplierTaxIdentifierCount(?, ?, ?, ?, ?)}")) {
          storedProcedure =>
            storedProcedure.setInt("p_application_id", request.applicationId)
            storedProcedure.setInt("p_item_number", request.itemNumber)
            storedProcedure.setString("p_supplier_tax_identifier", request.taxIdentifier)
            storedProcedure.setString("p_invoice_number", request.invoiceNumber)

            storedProcedure.registerOutParameter("p_count", OracleTypes.NUMBER)
            storedProcedure.execute()
            storedProcedure.getInt("p_count")
        }
      }
    }
  }

  def addApplication(applicationRequest: ApplicationRequest, vrn: String): Future[ApplicationResponse] = {
    logger.info(s"************* calling stored procedure to create application for VRN: $vrn")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.addApplication(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {
          stmt =>
            // Set input parameters
            stmt.setString("p_applicant_vat_reg_number", vrn)
            stmt.setString("p_refunding_country_code", applicationRequest.refundingCountryCode.orNull)
            stmt.setTimestamp("p_period_start_date", applicationRequest.periodStartDate.map(java.sql.Timestamp.valueOf).orNull)
            stmt.setTimestamp("p_period_end_date", applicationRequest.periodEndDate.map(java.sql.Timestamp.valueOf).orNull)
            stmt.setString("p_applicant_email_address", applicationRequest.applicantEmailAddress.orNull)
            stmt.setString("p_applicant_telephone_num", applicationRequest.applicantTelephoneNumber.orNull)
            stmt.setString("p_application_language", applicationRequest.applicationLanguage.orNull)
            stmt.setString("p_representative_id", applicationRequest.representativeId.orNull)
            stmt.setString("p_representative_country", applicationRequest.representativeCountryCode.orNull)
            stmt.setString("p_representative_email_address", applicationRequest.representativeEmailAddress.orNull)
            stmt.setString("p_representative_id_type", applicationRequest.representativeIdType.orNull)
            stmt.setString("p_representative_telephone_num", applicationRequest.representativeTelephoneNumber.orNull)
            stmt.setString("p_bank_account_owner_name", applicationRequest.bankAccountOwnerName.orNull)
            stmt.setString("p_bank_account_owner_type", applicationRequest.bankAccountOwnerType.orNull)
            stmt.setString("p_iban_code", applicationRequest.iBanCode.orNull)
            stmt.setString("p_bic_code", applicationRequest.bicCode.orNull)
            stmt.setString("p_bank_account_currency_code", applicationRequest.bankAccountCurrencyCode.orNull)
            stmt.setString("p_business_activity_code1", applicationRequest.businessActivityCode1.orNull)
            stmt.setString("p_business_activity_code2", applicationRequest.businessActivityCode2.orNull)
            stmt.setString("p_business_activity_code3", applicationRequest.businessActivityCode3.orNull)
            stmt.registerOutParameter("p_application_id", OracleTypes.NUMBER)
            stmt.registerOutParameter("p_application_number", OracleTypes.VARCHAR)
            stmt.registerOutParameter("p_update_seq_number", OracleTypes.NUMBER)
            stmt.execute()

            ApplicationResponse(
              stmt.getInt("p_application_id"),
              stmt.getString("p_application_number"),
              stmt.getInt("p_update_seq_number")
            )
        }
      }
    }
  }

  def deleteApplication(request: uk.gov.hmrc.rdscandeproxy.euvat.models.requests.DeleteApplicationRequest): Future[Unit] = {
    logger.info(s"Calling stored procedure deleteApplication for applicationId: ${request.applicationId}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.deleteApplication(?,?)}")) { storedProcedure =>
          storedProcedure.setInt("p_application_id", request.applicationId)
          storedProcedure.setInt("p_update_seq_number", request.updateSequenceNumber)
          storedProcedure.execute()

        }
      }
    }
  }

  def addImportation(request: AddImportRequest): Future[AddImportResponse] = {
    logger.info(s"Calling stored procedure addImportation for applicationId: ${request.applicationId}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.addImportation(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {
          storedProcedure =>
            storedProcedure.setLong("p_application_id", request.applicationId)
            storedProcedure.setString("p_goods_description_category", request.goodsDescriptionCategory)
            request.goodsDescriptionText match {
              case Some(t) => storedProcedure.setString("p_goods_description_text", t)
              case None    => storedProcedure.setNull("p_goods_description_text", OracleTypes.VARCHAR)
            }
            request.importationSubcategory match {
              case Some(s) => storedProcedure.setString("p_importation_subcategory", s)
              case None    => storedProcedure.setNull("p_importation_subcategory", OracleTypes.VARCHAR)
            }
            request.hasSadReferenceNumber match {
              case Some(h) => storedProcedure.setString("p_has_sad_reference_number", h)
              case None    => storedProcedure.setNull("p_has_sad_reference_number", OracleTypes.VARCHAR)
            }
            request.sadReferenceNumber match {
              case Some(s) => storedProcedure.setString("p_sad_reference_number", s)
              case None    => storedProcedure.setNull("p_sad_reference_number", OracleTypes.VARCHAR)
            }
            request.supplierAddressLine1 match {
              case Some(a) => storedProcedure.setString("p_supplier_address_1", a)
              case None    => storedProcedure.setNull("p_supplier_address_1", OracleTypes.VARCHAR)
            }
            request.supplierAddressLine2 match {
              case Some(a) => storedProcedure.setString("p_supplier_address_2", a)
              case None    => storedProcedure.setNull("p_supplier_address_2", OracleTypes.VARCHAR)
            }
            request.supplierAddressLine3 match {
              case Some(a) => storedProcedure.setString("p_supplier_address_3", a)
              case None    => storedProcedure.setNull("p_supplier_address_3", OracleTypes.VARCHAR)
            }
            request.referenceInformation match {
              case Some(r) => storedProcedure.setString("p_reference_information", r)
              case None    => storedProcedure.setNull("p_reference_information", OracleTypes.VARCHAR)
            }
            request.issuingDate match {
              case Some(d) => storedProcedure.setDate("p_issuing_date", java.sql.Date.valueOf(d.toLocalDate))
              case None    => storedProcedure.setNull("p_issuing_date", java.sql.Types.DATE)
            }
            request.supplierName match {
              case Some(n) => storedProcedure.setString("p_supplier_name", n)
              case None    => storedProcedure.setNull("p_supplier_name", OracleTypes.VARCHAR)
            }
            request.supplierCountryCode match {
              case Some(c) => storedProcedure.setString("p_supplier_country_code", c)
              case None    => storedProcedure.setNull("p_supplier_country_code", OracleTypes.VARCHAR)
            }
            request.currencyCode match {
              case Some(c) => storedProcedure.setString("p_currency_code", c)
              case None    => storedProcedure.setNull("p_currency_code", OracleTypes.VARCHAR)
            }
            request.taxableAmount match {
              case Some(v) => storedProcedure.setBigDecimal("p_taxable_amount", v.bigDecimal)
              case None    => storedProcedure.setNull("p_taxable_amount", OracleTypes.NUMBER)
            }
            request.vatAmount match {
              case Some(v) => storedProcedure.setBigDecimal("p_vat_amount", v.bigDecimal)
              case None    => storedProcedure.setNull("p_vat_amount", OracleTypes.NUMBER)
            }
            request.deductibleVatAmount match {
              case Some(v) => storedProcedure.setBigDecimal("p_deductible_vat_amount", v.bigDecimal)
              case None    => storedProcedure.setNull("p_deductible_vat_amount", OracleTypes.NUMBER)
            }
            storedProcedure.setInt("p_update_seq_number", request.updateSequenceNumber)

            storedProcedure.registerOutParameter("p_item_number", OracleTypes.NUMBER)
            storedProcedure.registerOutParameter("p_update_seq_number", OracleTypes.NUMBER)

            storedProcedure.execute()

            val itemNumber = storedProcedure.getInt("p_item_number")
            val updateSeq = storedProcedure.getInt("p_update_seq_number")

            AddImportResponse(itemNumber = itemNumber, updateSequenceNumber = updateSeq)
        }
      }
    }
  }

  def deleteImport(request: DeleteImportRequest): Future[Int] = {
    logger.info(s"Calling stored procedure deleteImportationDetails for applicationId: ${request.applicationId} itemNumber: ${request.itemNumber}")
    Future {
      db.withConnection { connection =>
        Using.resource(
          connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.deleteImportationDetails(?, ?, ?)}")
        ) { storedProcedure =>
          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_item_number", request.itemNumber)
          storedProcedure.setInt("p_update_seq_number", request.updateSequenceNumber)
          storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.NUMERIC)
          storedProcedure.execute()
          storedProcedure.getInt("p_update_seq_number")
        }
      }
    }
  }

  def getPurchaseDetails(request: GetPurchaseDetailsRequest): Future[Option[GetPurchaseDetailsResponse]] = {
    logger.info(
      s"Calling stored procedure getPurchaseDetails for applicationId: ${request.applicationId} itemNumber: ${request.itemNumber}"
    )
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getPurchaseDetails(?, ?, ?, ?)}")) { storedProcedure =>
          // Set input parameters
          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_item_number", request.itemNumber)

          // Register output parameters
          storedProcedure.registerOutParameter("p_purchase_details", OracleTypes.CURSOR)
          storedProcedure.registerOutParameter("p_update_seq_number", OracleTypes.NUMBER)

          // Execute
          storedProcedure.execute()

          // Retrieve output parameters
          val updateSequenceNumber = storedProcedure.getInt("p_update_seq_number")
          val rs = storedProcedure.getObject("p_purchase_details", classOf[ResultSet])

          Using.resource(rs) { cursor =>
            if (cursor.next()) {
              Some(
                GetPurchaseDetailsResponse(
                  goodsDescriptionCode       = cursor.getString("goods_description_category"),
                  goodsDescriptionSubCode    = Option(cursor.getString("goods_description_subcategory")),
                  goodsDescriptionText       = Option(cursor.getString("goods_description_text")),
                  simplifiedInvoiceIndicator = Option(cursor.getString("simplified_invoice_indicator")),
                  supplierName               = Option(cursor.getString("supplier_name")),
                  supplierAddressLine1       = Option(cursor.getString("supplier_address_1")),
                  supplierAddressLine2       = Option(cursor.getString("supplier_address_2")),
                  supplierAddressLine3       = Option(cursor.getString("supplier_address_3")),
                  supplierVatNumber          = Option(cursor.getString("supplier_vat_reg_number")),
                  supplierTaxIdentifier      = Option(cursor.getString("supplier_tax_identifier")),
                  invoiceDate                = Option(cursor.getTimestamp("invoice_date")).map(_.toLocalDateTime),
                  invoiceNumber              = Option(cursor.getString("invoice_number")),
                  currencyCode               = Option(cursor.getString("currency_code")),
                  taxableAmount              = Option(cursor.getBigDecimal("taxable_amount")).map(BigDecimal(_)),
                  vatAmount                  = Option(cursor.getBigDecimal("vat_amount")).map(BigDecimal(_)),
                  deductibleVatAmount        = Option(cursor.getBigDecimal("deductible_vat_amount")).map(BigDecimal(_)),
                  updateSequenceNumber       = updateSequenceNumber
                )
              )
            } else {
              None
            }
          }
        }
      }
    }
  }

  def getImportationDetails(request: GetImportationDetailsRequest): Future[Option[GetImportationDetailsResponse]] = {
    logger.info(
      s"Calling stored procedure getImportationDetails for applicationId: ${request.applicationId} itemNumber: ${request.itemNumber}"
    )
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getImportationDetails(?, ?, ?, ?, ?)}")) { storedProcedure =>

          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_item_number", request.itemNumber)

          storedProcedure.registerOutParameter("p_importation_details", OracleTypes.CURSOR)
          storedProcedure.registerOutParameter("p_update_seq_number", OracleTypes.NUMBER)
          storedProcedure.registerOutParameter("p_period_start_date", OracleTypes.DATE)

          storedProcedure.execute()

          val updateSequenceNumber = storedProcedure.getInt("p_update_seq_number")
          val periodStartDate = storedProcedure.getDate("p_period_start_date").toLocalDate.atStartOfDay
          val rs = storedProcedure.getObject("p_importation_details", classOf[ResultSet])

          Using.resource(rs) { cursor =>
            if (cursor.next()) {
              Some(
                GetImportationDetailsResponse(
                  applicationId               = cursor.getLong("application_id"),
                  itemNumber                  = cursor.getInt("item_number"),
                  issuingDate                 = Option(cursor.getTimestamp("issuing_date")).map(_.toLocalDateTime),
                  supplierName                = Option(cursor.getString("supplier_name")),
                  supplierAddressLine1        = Option(cursor.getString("supplier_address_1")),
                  supplierAddressLine2        = Option(cursor.getString("supplier_address_2")),
                  supplierAddressLine3        = Option(cursor.getString("supplier_address_3")),
                  supplierCountryCode         = Option(cursor.getString("supplier_country_code")),
                  goodsDescriptionCategory    = cursor.getString("goods_description_category"),
                  goodsDescriptionSubcategory = Option(cursor.getString("goods_description_subcategory")),
                  goodsDescriptionText        = Option(cursor.getString("goods_description_text")),
                  hasSadReferenceNumber       = Option(cursor.getString("has_sad_reference_number")),
                  sadReferenceNumber          = Option(cursor.getString("sad_reference_number")),
                  referenceInformation        = Option(cursor.getString("reference_information")),
                  currencyCode                = Option(cursor.getString("currency_code")),
                  taxableAmount               = Option(cursor.getBigDecimal("taxable_amount")).map(BigDecimal(_)),
                  vatAmount                   = Option(cursor.getBigDecimal("vat_amount")).map(BigDecimal(_)),
                  deductibleVatAmount         = Option(cursor.getBigDecimal("deductible_vat_amount")).map(BigDecimal(_)),
                  updateSequenceNumber        = updateSequenceNumber,
                  periodStartDate             = periodStartDate
                )
              )
            } else {
              None
            }
          }
        }
      }
    }
  }

  def addPurchase(request: AddPurchaseRequest): Future[AddPurchaseResponse] = {
    logger.info(s"************* calling stored procedure to add purchase for applicationId: ${request.applicationId}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.addPurchase(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) { stmt =>
          // Set input parameters
          stmt.setLong("p_application_id", request.applicationId)
          stmt.setString("p_goods_description_category", request.goodsDescriptionCategory)
          stmt.setString("p_goods_description_text", request.goodsDescriptionText.orNull)
          stmt.setString("p_purchase_subcategory", request.purchaseSubcategory.orNull)
          stmt.setString("p_simplified_invoice_indicator", request.simplifiedInvoiceIndicator.orNull)
          stmt.setString("p_supplier_name", request.supplierName.orNull)
          stmt.setString("p_supplier_address_1", request.supplierAddress1.orNull)
          stmt.setString("p_supplier_address_2", request.supplierAddress2.orNull)
          stmt.setString("p_supplier_address_3", request.supplierAddress3.orNull)
          stmt.setString("p_supplier_vat_reg_number", request.supplierVatRegNumber.orNull)
          stmt.setString("p_supplier_tax_identifier", request.supplierTaxIdentifier.orNull)
          stmt.setTimestamp("p_invoice_date", request.invoiceDate.map(java.sql.Timestamp.valueOf).orNull)
          stmt.setString("p_invoice_number", request.invoiceNumber.orNull)
          stmt.setString("p_currency_code", request.currencyCode.orNull)
          stmt.setBigDecimal("p_taxable_amount", request.taxableAmount.map(_.bigDecimal).orNull)
          stmt.setBigDecimal("p_vat_amount", request.vatAmount.map(_.bigDecimal).orNull)
          stmt.setBigDecimal("p_deductible_vat_amount", request.deductibleVatAmount.map(_.bigDecimal).orNull)
          stmt.setInt("p_update_seq_number", request.updateSequenceNumber)
          stmt.registerOutParameter("p_update_seq_number", java.sql.Types.NUMERIC)
          stmt.registerOutParameter("p_item_number", java.sql.Types.NUMERIC)
          stmt.execute()

          AddPurchaseResponse(
            stmt.getInt("p_item_number"),
            stmt.getInt("p_update_seq_number")
          )
        }
      }
    }
  }

  def getSupplierVrnCount(request: SupplierVrnCountRequest): Future[SupplierVrnCountResponse] = {
    logger.info(s"Calling stored procedure getSupplierVRNCount for applicationId: ${request.applicationId}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getSupplierVRNCount(?, ?, ?, ?, ?)}")) { storedProcedure =>
          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_item_number", request.itemNumber)
          storedProcedure.setString("p_supplier_vat_reg_number", request.vatNumber)
          storedProcedure.setString("p_invoice_number", request.invoiceNumber)
          storedProcedure.registerOutParameter("p_count", OracleTypes.NUMBER)
          storedProcedure.execute()
          SupplierVrnCountResponse(storedProcedure.getInt("p_count"))
        }
      }
    }
  }

  private def callUpdatePurchaseDetails(
    connection: Connection,
    request: UpdatePurchaseDetailsRequest,
    currentSeq: Int
  ): Int = {
    logger.info(s"Calling stored procedure updatePurchaseDetails for application: ${request.applicationId} item: ${request.itemNumber}")
    Using.resource(
      connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDetails(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")
    ) { storedProcedure =>
      storedProcedure.setLong("p_application_id", request.applicationId)
      storedProcedure.setInt("p_item_number", request.itemNumber)
      storedProcedure.setString("p_simplified_invoice_indicator", request.simplifiedInvoiceIndicator.orNull)
      storedProcedure.setString("p_supplier_name", request.supplierName.orNull)
      storedProcedure.setString("p_supplier_address_1", request.supplierAddress1.orNull)
      storedProcedure.setString("p_supplier_address_2", request.supplierAddress2.orNull)
      storedProcedure.setString("p_supplier_address_3", request.supplierAddress3.orNull)
      storedProcedure.setString("p_supplier_vat_reg_number", request.supplierVatRegNumber.orNull)
      storedProcedure.setString("p_supplier_tax_identifier", request.supplierTaxIdentifier.orNull)
      request.invoiceDate match {
        case Some(dt) => storedProcedure.setDate("p_invoice_date", java.sql.Date.valueOf(dt.toLocalDate))
        case None     => storedProcedure.setNull("p_invoice_date", java.sql.Types.DATE)
      }
      storedProcedure.setString("p_invoice_number", request.invoiceNumber.orNull)
      storedProcedure.setString("p_currency_code", request.currencyCode.orNull)
      request.taxableAmount match {
        case Some(amount) => storedProcedure.setBigDecimal("p_taxable_amount", amount.bigDecimal)
        case None         => storedProcedure.setNull("p_taxable_amount", java.sql.Types.NUMERIC)
      }
      request.vatAmount match {
        case Some(amount) => storedProcedure.setBigDecimal("p_vat_amount", amount.bigDecimal)
        case None         => storedProcedure.setNull("p_vat_amount", java.sql.Types.NUMERIC)
      }
      request.deductibleVatAmount match {
        case Some(amount) => storedProcedure.setBigDecimal("p_deductible_vat_amount", amount.bigDecimal)
        case None         => storedProcedure.setNull("p_deductible_vat_amount", java.sql.Types.NUMERIC)
      }
      storedProcedure.setInt("p_update_seq_number", currentSeq)
      storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
      storedProcedure.execute()
      storedProcedure.getInt("p_update_seq_number")
    }
  }

  private def updatePurchaseSubCategory(connection: Connection,
                                        applicationId: Long,
                                        itemNumber: Int,
                                        purchaseSubcategory: String,
                                        updateSeqNumber: Int
                                       ): Int = {
    Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseSubCategory(?, ?, ?, ?)}")) { storedProcedure =>
      storedProcedure.setLong("p_application_id", applicationId)
      storedProcedure.setInt("p_item_number", itemNumber)
      storedProcedure.setString("p_purchase_subcategory", purchaseSubcategory)
      storedProcedure.setInt("p_update_seq_number", updateSeqNumber)
      storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
      storedProcedure.execute()
      storedProcedure.getInt("p_update_seq_number")
    }
  }

  private def updatePurchaseDescription(connection: Connection,
                                        applicationId: Long,
                                        itemNumber: Int,
                                        purchaseDescription: String,
                                        updateSeqNumber: Int
                                       ): Int = {
    Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseDescription(?, ?, ?, ?)}")) { storedProcedure =>
      storedProcedure.setLong("p_application_id", applicationId)
      storedProcedure.setInt("p_item_number", itemNumber)
      storedProcedure.setString("p_goods_description_text", purchaseDescription)
      storedProcedure.setInt("p_update_seq_number", updateSeqNumber)
      storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
      storedProcedure.execute()
      storedProcedure.getInt("p_update_seq_number")
    }
  }

  private def updatePurchaseCategory(connection: Connection,
                                     applicationId: Long,
                                     itemNumber: Int,
                                     goodsDescriptionCategory: String,
                                     updateSeqNumber: Int
                                    ): Int = {
    Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updatePurchaseCategory(?, ?, ?, ?)}")) { storedProcedure =>
      storedProcedure.setLong("p_application_id", applicationId)
      storedProcedure.setInt("p_item_number", itemNumber)
      storedProcedure.setString("p_goods_description_category", goodsDescriptionCategory)
      storedProcedure.setInt("p_update_seq_number", updateSeqNumber)
      storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
      storedProcedure.execute()
      storedProcedure.getInt("p_update_seq_number")
    }
  }

  def updatePurchaseDetails(request: UpdatePurchaseDetailsRequest): Future[Int] = {
    logger.info(s"Calling stored procedure updatePurchaseDetails for applicationId: ${request.applicationId}")
    Future {
      db.withTransaction { connection =>
        val updateSequenceAfterCategoryUpdate = updatePurchaseCategory(
          connection,
          request.applicationId,
          request.itemNumber,
          request.goodsDescriptionCategory,
          request.updateSequenceNumber
        )

        val updateSequenceAfterSubCategoryUpdate = request.goodsDescriptionSubCategory.fold(updateSequenceAfterCategoryUpdate) { sub =>
          updatePurchaseSubCategory(connection, request.applicationId, request.itemNumber, sub, updateSequenceAfterCategoryUpdate)
        }

        val updateSequenceAfterDescriptionUpdate = request.goodsDescriptionText.fold(updateSequenceAfterSubCategoryUpdate) { text =>
          updatePurchaseDescription(connection, request.applicationId, request.itemNumber, text, updateSequenceAfterSubCategoryUpdate)
        }

        callUpdatePurchaseDetails(connection, request, updateSequenceAfterDescriptionUpdate)
      }
    }
  }

  def getPurchaseImportList(request: PurchaseImportListRequest): Future[PurchaseImportListResponse] = {
    logger.info(s"Calling stored procedure getPurchaseImportList for applicationId: ${request.applicationId}")
    Future {
      db.withConnection { connection =>
        Using.resource(
          connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL_ADDITIONAL.getAllPurchasesAndImportations(?, ?, ?, ?, ?, ?, ?, ?)}")
        ) { storedProcedure =>
          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_order_by", request.orderBy)
          storedProcedure.setString("p_sort_order", request.sortOrder)
          storedProcedure.setInt("p_start_at", request.startAt)
          storedProcedure.setInt("p_max_number", request.maxNumber.getOrElse(1))
          storedProcedure.registerOutParameter("p_purch_and_imp_list", OracleTypes.CURSOR)
          storedProcedure.registerOutParameter("p_total_items", OracleTypes.NUMBER)
          storedProcedure.registerOutParameter("p_total_deductible_vat", OracleTypes.DECIMAL)
          storedProcedure.execute()

          val totalItems = storedProcedure.getInt("p_total_items")
          val totalDeductibleVat: BigDecimal = storedProcedure.getBigDecimal("p_total_deductible_vat")
          val rs = storedProcedure.getObject("p_purch_and_imp_list").asInstanceOf[ResultSet]

          val purchaseImports: List[PurchaseImport] = Using.resource(rs) { cursor =>
            Iterator
              .continually(cursor.next())
              .takeWhile(identity)
              .map(_ =>
                PurchaseImport(
                  itemNumber                  = cursor.getInt("item_number"),
                  itemType                    = cursor.getString("item_type"),
                  goodsDescriptionCategory    = cursor.getString("goods_description_category"),
                  goodsDescriptionSubCategory = Option(cursor.getString("goods_description_subcategory")),
                  currencyCode                = cursor.getString("currency_code"),
                  taxableAmount               = Option(cursor.getBigDecimal("taxable_amount")).map(BigDecimal(_)).getOrElse(BigDecimal(0)),
                  vatAmount                   = Option(cursor.getBigDecimal("vat_amount")).map(BigDecimal(_)).getOrElse(BigDecimal(0)),
                  deductibleVatAmount         = Option(cursor.getBigDecimal("deductible_vat_amount")).map(BigDecimal(_)).getOrElse(BigDecimal(0))
                )
              )
              .toList
          }
          PurchaseImportListResponse(purchaseImportList = purchaseImports, totalItems = totalItems, totalVatClaims = totalDeductibleVat)
        }
      }
    }
  }

  private def callUpdateApplicationDetails(
    connection: Connection,
    request: UpdateApplicationDetailsRequest,
    currentSeq: Int
  ): Int = {
    logger.info(s"Calling stored procedure updateApplicationDetails for applicationId: ${request.applicationId}")
    Using.resource(
      connection.prepareCall(
        "{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updateApplicationDetails(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"
      )
    ) { storedProcedure =>
      storedProcedure.setLong("p_application_id", request.applicationId)
      storedProcedure.setString("p_refunding_country_code", request.refundingCountry)
      storedProcedure.setTimestamp("p_period_start_date", java.sql.Timestamp.valueOf(request.periodStartDate))
      storedProcedure.setTimestamp("p_period_end_date", java.sql.Timestamp.valueOf(request.periodEndDate))
      storedProcedure.setString("p_applicant_email_address", request.applicantEmailAddress)
      storedProcedure.setString("p_applicant_telephone_num", request.applicantPhoneNumber.orNull)
      storedProcedure.setString("p_representative_country", request.representativeCountry.orNull)
      storedProcedure.setString("p_representative_email_address", request.representativeEmailAddress.orNull)
      storedProcedure.setString("p_representative_telephone_num", request.representativePhoneNumber.orNull)
      storedProcedure.setString("p_bank_account_owner_name", request.bankAccountOwnerName.orNull)
      storedProcedure.setString("p_bank_account_owner_type", request.bankAccountOwnerType.orNull)
      storedProcedure.setString("p_iban_code", request.ibanCode.orNull)
      storedProcedure.setString("p_bic_code", request.bicCode.orNull)
      storedProcedure.setString("p_bank_account_currency_code", request.bankAccountCurrencyCode.orNull)
      storedProcedure.setString("p_business_activity_code2", request.businessActivityCode2.orNull)
      storedProcedure.setString("p_business_activity_code3", request.businessActivityCode3.orNull)
      storedProcedure.setString("p_cipher_text", request.cipherText.orNull)
      storedProcedure.setString("p_encryption_status", request.encryptionStatus.orNull)
      storedProcedure.setInt("p_update_seq_number", currentSeq)
      storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
      storedProcedure.execute()
      storedProcedure.getInt("p_update_seq_number")
    }
  }

  private def callUpdateApplicationLanguage(
    connection: Connection,
    applicationId: Long,
    applicationLanguage: String,
    currentSeq: Int
  ): Int = {
    logger.info(s"Calling stored procedure updateApplicationLanguage for applicationId: $applicationId")
    Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.updateApplicationLanguage(?, ?, ?)}")) { storedProcedure =>
      storedProcedure.setLong("p_application_id", applicationId)
      storedProcedure.setString("p_application_language", applicationLanguage)
      storedProcedure.setInt("p_update_seq_number", currentSeq)
      storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
      storedProcedure.execute()
      storedProcedure.getInt("p_update_seq_number")
    }
  }

  def updateApplicationDetails(request: UpdateApplicationDetailsRequest): Future[Int] =
    Future {
      db.withTransaction { connection =>
        val seqAfterLanguage =
          callUpdateApplicationLanguage(connection, request.applicationId, request.applicationLanguage, request.updateSequenceNumber)

        callUpdateApplicationDetails(connection, request, seqAfterLanguage)
      }
    }

  def deletePurchase(request: DeletePurchaseRequest): Future[Int] = {
    logger.info(s"Calling stored procedure deletePurchaseDetails for applicationId: ${request.applicationId} itemNumber: ${request.itemNumber}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_UPDATE.deletePurchaseDetails(?, ?, ?)}")) { storedProcedure =>
          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_item_number", request.itemNumber)
          storedProcedure.setInt("p_update_seq_number", request.updateSequenceNumber)
          storedProcedure.registerOutParameter("p_update_seq_number", java.sql.Types.INTEGER)
          storedProcedure.execute()
          storedProcedure.getInt("p_update_seq_number")
        }
      }
    }
  }

  def getSadReferenceCount(request: SadReferenceCountRequest): Future[SadReferenceCountResponse] = {
    logger.info(s"Calling stored procedure getSadReferenceCount for applicationId: ${request.applicationId}")
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getSadReferenceCount(?, ?, ?, ?)}")) { storedProcedure =>
          storedProcedure.setLong("p_application_id", request.applicationId)
          storedProcedure.setInt("p_item_number", request.itemNumber)
          storedProcedure.setString("p_sad_reference_number", request.sadReferenceNumber)
          storedProcedure.registerOutParameter("p_count", OracleTypes.NUMBER)
          storedProcedure.execute()
          SadReferenceCountResponse(storedProcedure.getInt("p_count"))
        }
      }
    }
  }
}
