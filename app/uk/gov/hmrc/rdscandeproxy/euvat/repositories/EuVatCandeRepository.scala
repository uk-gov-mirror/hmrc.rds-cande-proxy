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
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.{AddPurchaseRequest, AddPurchaseResponse, ApplicationRequest, LatestApplicationRequest}
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.{LatestApplication, LatestApplicationResponse}
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.ApplicationResponse

import java.sql.ResultSet
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

  def getSupplierTaxIdentifierDuplicateCount(
    request: uk.gov.hmrc.rdscandeproxy.euvat.models.requests.SupplierTaxIdentifierCountRequest
  ): Future[Int] = {
    logger.info(
      s"Calling stored procedure getSupplierTaxIdentifierCount for applicationId: ${request.applicationId} itemNumber: ${request.itemNumber}"
    )
    Future {
      db.withConnection { connection =>
        Using.resource(connection.prepareCall("{call EUVAT_FILE_DATA.EU_VAT_RETRIEVAL.getSupplierTaxIdentifierCount(?, ?, ?, ?, ?)}")) {
          storedProcedure =>
            // input params
            storedProcedure.setInt("p_application_id", request.applicationId)
            storedProcedure.setInt("p_item_number", request.itemNumber)
            storedProcedure.setString("p_supplier_tax_identifier", request.taxIdentifier)
            storedProcedure.setString("p_invoice_number", request.invoiceNumber)

            // out param
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
            logger.info("Data successfully saved in database")

            ApplicationResponse(
              stmt.getInt("p_application_id"),
              stmt.getString("p_application_number"),
              stmt.getInt("p_update_seq_number")
            )
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
          logger.info("Data successfully saved in database")

          AddPurchaseResponse(
            stmt.getInt("p_item_number"),
            stmt.getInt("p_update_seq_number")
          )
        }
      }
    }
  }

}
