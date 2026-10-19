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

package uk.gov.hmrc.rdscandeproxy.euvat.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.{should, shouldBe}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.*
import uk.gov.hmrc.rdscandeproxy.euvat.base.SpecBase
import uk.gov.hmrc.rdscandeproxy.euvat.models.PurchaseImport
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.*
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.*
import uk.gov.hmrc.rdscandeproxy.euvat.services.EuVatService

import java.time.LocalDateTime
import scala.concurrent.Future

class EuVatControllerSpec extends SpecBase with MockitoSugar {

  "EuVatController" - {

    "getLatestApplications" - {
      "return 200 with JSON when service returns successful latest applications response" in new SetUp {
        when(mockEuVatService.getLatestApplications(any[LatestApplicationRequest]))
          .thenReturn(Future.successful(latestAppResponse))

        val result: Future[Result] = controller.getLatestApplications()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(latestAppRequest))
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(latestAppResponse)
      }

      "return 400 when service returns no records found if applicantVatRegNumber empty" in new SetUp {
        val result: Future[Result] = controller.getLatestApplications()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(latestAppRequest.copy(applicantVatRegNumber = "")))
        )

        status(result) shouldBe BAD_REQUEST
      }

      "return 400 when request body is invalid" in new SetUp {
        val result: Future[Result] = controller.getLatestApplications()(
          fakeRequest.withMethod("POST").withJsonBody(Json.obj("invalid" -> "body"))
        )

        status(result) shouldBe BAD_REQUEST
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getLatestApplications(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.getLatestApplications()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(latestAppRequest))
        )

        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe "Failed to create latest applications"
      }
    }

    "add application" - {
      "return 200 and a successful response when DB returns records" in new SetUp {
        when(mockEuVatService.addApplication(any(), any()))
          .thenReturn(Future.successful(applicationResponse))

        val result: Future[Result] = controller.addApplication()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(appRequest))
        )

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(applicationResponse)

      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.addApplication()(
          fakeRequest.withMethod("POST")
        )

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 and log error when DB call fails" in new SetUp {
        val exception = new RuntimeException("DB error")
        when(mockEuVatService.addApplication(any(), any()))
          .thenReturn(Future.failed(exception))
        val result: Future[Result] = controller.addApplication()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(appRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to create refund application")
      }
    }

    "add purchase" - {
      "return 200 and a successful response when DB returns records" in new SetUp {
        when(mockEuVatService.addPurchase(any()))
          .thenReturn(Future.successful(purchaseResponse))

        val result: Future[Result] = controller.addPurchase()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(purchaseRequest))
        )

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(purchaseResponse)

      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.addPurchase()(
          fakeRequest.withMethod("POST")
        )

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 and log error when DB call fails" in new SetUp {
        val exception = new RuntimeException("DB error")
        when(mockEuVatService.addPurchase(any()))
          .thenReturn(Future.failed(exception))
        val result: Future[Result] = controller.addPurchase()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(purchaseRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to add purchase")
      }
    }

    "add import" - {
      "return 200 and a successful response when DB returns records" in new SetUp {
        val importRequest = AddImportRequest(
          applicationId            = 123456,
          goodsDescriptionCategory = "3",
          goodsDescriptionText     = Some("Electronics"),
          updateSequenceNumber     = 1
        )

        val importResponse = AddImportResponse(itemNumber = 4, updateSequenceNumber = 1)

        when(mockEuVatService.addImport(any())).thenReturn(Future.successful(importResponse))

        val result: Future[Result] = controller.addImport()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(importRequest))
        )

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(importResponse)
      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.addImport()(fakeRequest.withMethod("POST"))

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 and log error when DB call fails" in new SetUp {
        val exception = new RuntimeException("DB error")
        when(mockEuVatService.addImport(any())).thenReturn(Future.failed(exception))

        val importRequest = AddImportRequest(
          applicationId            = 123456,
          goodsDescriptionCategory = "3",
          goodsDescriptionText     = Some("Electronics"),
          updateSequenceNumber     = 1
        )

        val result: Future[Result] = controller.addImport()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(importRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to add import")
      }
    }

    "getPurchaseDetails" - {
      "return 200 and the purchase details when the purchase record exists" in new SetUp {
        when(mockEuVatService.getPurchaseDetails(any()))
          .thenReturn(Future.successful(Some(purchaseDetailsResponse)))

        val result: Future[Result] = controller.getPurchaseDetails()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(purchaseDetailsRequest))
        )

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(purchaseDetailsResponse)
      }

      "return 500 when no purchase record exists for the applicationId and itemNumber" in new SetUp {
        when(mockEuVatService.getPurchaseDetails(any()))
          .thenReturn(Future.successful(None))

        val result: Future[Result] = controller.getPurchaseDetails()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(purchaseDetailsRequest))
        )

        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe "Failed to retrieve purchase details"
      }

      "return 400 when request body is invalid" in new SetUp {
        val result: Future[Result] = controller.getPurchaseDetails()(
          fakeRequest.withMethod("POST").withJsonBody(Json.obj("invalid" -> "body"))
        )

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getPurchaseDetails(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.getPurchaseDetails()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(purchaseDetailsRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to retrieve purchase details")
      }
    }

    "getImportationDetails" - {
      "return 200 and the importation details when the importation record exists" in new SetUp {
        when(mockEuVatService.getImportationDetails(any()))
          .thenReturn(Future.successful(Some(importationDetailsResponse)))

        val result: Future[Result] = controller.getImportationDetails(404, 4)(fakeRequest.withMethod("GET"))

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(importationDetailsResponse)
      }

      "return 500 when no importation record exists for the applicationId and itemNumber" in new SetUp {
        when(mockEuVatService.getImportationDetails(any()))
          .thenReturn(Future.successful(None))

        val result: Future[Result] = controller.getImportationDetails(404, 99)(fakeRequest.withMethod("GET"))

        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) shouldBe "Failed to retrieve importation details"
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getImportationDetails(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.getImportationDetails(404, 4)(fakeRequest.withMethod("GET"))

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to retrieve importation details")
      }
    }

    "getSupplierVrnCount" - {
      "return 200 with JSON when service returns the count" in new SetUp {
        when(mockEuVatService.getSupplierVrnCount(any()))
          .thenReturn(Future.successful(vrnCountResponse))

        val result: Future[Result] = controller.getSupplierVrnCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(vrnCountRequest))
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(vrnCountResponse)
      }

      "return 400 when request body is invalid" in new SetUp {
        val result: Future[Result] = controller.getSupplierVrnCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.obj("invalid" -> "body"))
        )

        status(result) shouldBe BAD_REQUEST
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getSupplierVrnCount(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.getSupplierVrnCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(vrnCountRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to retrieve supplier VRN count")
      }
    }

    "getSupplierTaxIdentifierCount" - {
      "return 200 with JSON when service returns count" in new SetUp {
        when(mockEuVatService.getSupplierTaxIdentifierDuplicateCount(any()))
          .thenReturn(Future.successful(4))

        val json: JsObject = Json.obj(
          "applicationId" -> 133,
          "itemNumber"    -> 4,
          "taxIdentifier" -> "500000881",
          "invoiceNumber" -> "a444"
        )

        val result: Future[Result] = controller.getSupplierTaxIdentifierCount()(
          fakeRequest.withMethod("POST").withJsonBody(json)
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.obj("duplicateCount" -> 4)
      }

      "return 400 when request body is invalid" in new SetUp {
        val result: Future[Result] = controller.getSupplierTaxIdentifierCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.obj("invalid" -> "body"))
        )

        status(result) shouldBe BAD_REQUEST
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getSupplierTaxIdentifierDuplicateCount(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val json: JsObject = Json.obj(
          "applicationId" -> 133,
          "itemNumber"    -> 4,
          "taxIdentifier" -> "500000881",
          "invoiceNumber" -> "a444"
        )

        val result: Future[Result] = controller.getSupplierTaxIdentifierCount()(
          fakeRequest.withMethod("POST").withJsonBody(json)
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to retrieve duplicate count")
      }
    }

    "updatePurchaseDetails" - {
      "return 200 and a successful response when DB returns records" in new SetUp {
        val req: UpdatePurchaseDetailsRequest = UpdatePurchaseDetailsRequest(
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

        when(mockEuVatService.updatePurchaseDetails(any())).thenReturn(Future.successful(UpdatePurchaseDetailsResponse(2)))

        val result: Future[Result] = controller.updatePurchaseDetails()(
          fakeRequest.withMethod("PUT").withJsonBody(Json.toJson(req))
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(UpdatePurchaseDetailsResponse(2))
      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.updatePurchaseDetails()(
          fakeRequest.withMethod("PUT")
        )

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 and log error when DB call fails" in new SetUp {
        val exception = new RuntimeException("DB error")
        when(mockEuVatService.updatePurchaseDetails(any())).thenReturn(Future.failed(exception))
        val req: UpdatePurchaseDetailsRequest = UpdatePurchaseDetailsRequest(
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

        val result: Future[Result] = controller.updatePurchaseDetails()(
          fakeRequest.withMethod("PUT").withJsonBody(Json.toJson(req))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to update purchase details")
      }
    }

    "getPurchaseImportList" - {
      val purchaseImport = PurchaseImport(123, "2", Some("2.3"), "EU", BigDecimal(300), BigDecimal(200), BigDecimal(100), "P")
      "return 200 with JSON when service returns count" in new SetUp {
        when(mockEuVatService.getPurchaseImportList(any())).thenReturn(Future.successful(PurchaseImportListResponse(List(purchaseImport), 1, 100)))

        val json: JsObject = Json.obj(
          "applicationId" -> 133,
          "orderBy"       -> 4
        )

        val result: Future[Result] = controller.getPurchaseImportList()(
          fakeRequest.withMethod("POST").withJsonBody(json)
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(PurchaseImportListResponse(List(purchaseImport), 1, 100))
      }

      "return 400 when request body is invalid" in new SetUp {
        val result: Future[Result] = controller.getPurchaseImportList()(
          fakeRequest.withMethod("POST").withJsonBody(Json.obj("invalid" -> "body"))
        )

        status(result) shouldBe BAD_REQUEST
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getPurchaseImportList(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val json: JsObject = Json.obj(
          "applicationId" -> 133
        )

        val result: Future[Result] = controller.getPurchaseImportList()(
          fakeRequest.withMethod("POST").withJsonBody(json)
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to retrieve purchase import list")
      }
    }

    "deleteApplication" - {
      "return 200 when service deletes the application" in new SetUp {
        when(mockEuVatService.deleteApplication(any())).thenReturn(Future.successful(()))

        val json: JsValue = Json.toJson(DeleteApplicationRequest(applicationId = 123, updateSequenceNumber = 1))

        val result: Future[Result] = controller.deleteApplication()(
          fakeRequest.withMethod("DELETE").withJsonBody(json)
        )

        status(result)          shouldBe OK
        contentAsString(result) shouldBe ""
      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.deleteApplication()(fakeRequest.withMethod("DELETE"))

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.deleteApplication(any())).thenReturn(Future.failed(new RuntimeException("DB error")))

        val json: JsValue = Json.toJson(DeleteApplicationRequest(applicationId = 123, updateSequenceNumber = 1))

        val result: Future[Result] = controller.deleteApplication()(
          fakeRequest.withMethod("DELETE").withJsonBody(json)
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to delete refund application")
      }
    }

    "updateApplicationDetails" - {
      "return 200 with the new update sequence number when the update succeeds" in new SetUp {
        when(mockEuVatService.updateApplicationDetails(any()))
          .thenReturn(Future.successful(updateAppDetailsResponse))

        val result: Future[Result] = controller.updateApplicationDetails()(
          fakeRequest.withMethod("PUT").withJsonBody(Json.toJson(updateAppDetailsRequest))
        )

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(updateAppDetailsResponse)
      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.updateApplicationDetails()(
          fakeRequest.withMethod("PUT")
        )

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 400 when a mandatory field is missing" in new SetUp {
        val json: JsObject = Json.toJson(updateAppDetailsRequest).as[JsObject] - "refundingCountry"

        val result: Future[Result] = controller.updateApplicationDetails()(
          fakeRequest.withMethod("PUT").withJsonBody(json)
        )

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 when the service fails" in new SetUp {
        when(mockEuVatService.updateApplicationDetails(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.updateApplicationDetails()(
          fakeRequest.withMethod("PUT").withJsonBody(Json.toJson(updateAppDetailsRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to update application details")
      }
    }

    "deletePurchase" - {
      "return 200 with the new update sequence number" in new SetUp {
        when(mockEuVatService.deletePurchase(any())).thenReturn(Future.successful(DeletePurchaseResponse(2)))

        val result: Future[Result] = controller.deletePurchase()(
          fakeRequest.withMethod("DELETE").withJsonBody(Json.toJson(deletePurchaseRequest))
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(DeletePurchaseResponse(2))
      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.deletePurchase()(fakeRequest.withMethod("DELETE"))

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 when the service fails" in new SetUp {
        when(mockEuVatService.deletePurchase(any())).thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.deletePurchase()(
          fakeRequest.withMethod("DELETE").withJsonBody(Json.toJson(deletePurchaseRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to delete purchase")
      }
    }

    "getSadReferenceCount" - {
      "return 200 with JSON when service returns the count" in new SetUp {
        when(mockEuVatService.getSadReferenceCount(any()))
          .thenReturn(Future.successful(sadReferenceCountResponse))

        val result: Future[Result] = controller.getSadReferenceCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(sadReferenceCountRequest))
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(sadReferenceCountResponse)
      }

      "return 400 when request body is invalid" in new SetUp {
        val result: Future[Result] = controller.getSadReferenceCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.obj("invalid" -> "body"))
        )

        status(result) shouldBe BAD_REQUEST
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.getSadReferenceCount(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val result: Future[Result] = controller.getSadReferenceCount()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(sadReferenceCountRequest))
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to retrieve SAD reference count")
      }
    }

    "deleteImport" - {
      "return 200 with the new update sequence number when the delete succeeds" in new SetUp {
        when(mockEuVatService.deleteImport(any()))
          .thenReturn(Future.successful(DeleteImportResponse(2)))

        val json: JsValue = Json.toJson(DeleteImportRequest(applicationId = 404L, itemNumber = 4, updateSequenceNumber = 1))

        val result: Future[Result] = controller.deleteImport()(
          fakeRequest.withMethod("DELETE").withJsonBody(json)
        )

        status(result)        shouldBe OK
        contentType(result)   shouldBe Some("application/json")
        contentAsJson(result) shouldBe Json.toJson(DeleteImportResponse(2))
      }

      "return 400 when request body is missing" in new SetUp {
        val result: Future[Result] = controller.deleteImport()(fakeRequest.withMethod("DELETE"))

        status(result)          shouldBe BAD_REQUEST
        contentAsString(result) shouldBe "Invalid request body"
      }

      "return 500 when service throws exception" in new SetUp {
        when(mockEuVatService.deleteImport(any()))
          .thenReturn(Future.failed(new RuntimeException("DB error")))

        val json: JsValue = Json.toJson(DeleteImportRequest(applicationId = 404L, itemNumber = 4, updateSequenceNumber = 1))

        val result: Future[Result] = controller.deleteImport()(
          fakeRequest.withMethod("DELETE").withJsonBody(json)
        )

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("Failed to delete import")
      }
    }

  }

  private class SetUp {
    val mockEuVatService: EuVatService = mock[EuVatService]
    val controller = new EuVatController(fakeAuthAction, mockEuVatService, cc)

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
    val applicationResponse: ApplicationResponse = ApplicationResponse(1, "GB9999991", 1)

    val latestAppRequest: LatestApplicationRequest = LatestApplicationRequest(
      applicantVatRegNumber = "123456789",
      refundingCountry      = Some("LV"),
      startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
      endDate               = Some(LocalDateTime.of(2025, 5, 31, 23, 59)),
      representativeId      = None,
      maxNumber             = 10,
      orderBy               = None,
      sortOrder             = None,
      startAt               = None
    )

    val latestAppResponse: LatestApplicationResponse = LatestApplicationResponse(
      totalApplication = 1,
      applications = List(
        LatestApplication(
          applicationId        = 1,
          refundingCountryCode = "DE",
          periodStartDate      = LocalDateTime.of(2025, 3, 1, 0, 0),
          periodEndDate        = LocalDateTime.of(2025, 5, 31, 23, 59),
          applicationNumber    = "1",
          applicationStatus    = Some("D"),
          submissionStatus     = None,
          applicationVersion   = LocalDateTime.of(2025, 5, 31, 23, 59)
        )
      )
    )

    val updateAppDetailsRequest: UpdateApplicationDetailsRequest = UpdateApplicationDetailsRequest(
      applicationId              = 133,
      applicationLanguage        = "en",
      refundingCountry           = "LV",
      periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0),
      periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
      applicantEmailAddress      = "test@hotmail.com",
      applicantPhoneNumber       = Some("01952233299"),
      representativeCountry      = Some("AO"),
      representativeEmailAddress = Some("johnbloggs@hotmail.com"),
      representativePhoneNumber  = Some("01952233248"),
      bankAccountOwnerName       = Some("test account 1"),
      bankAccountOwnerType       = Some("applicant"),
      ibanCode                   = Some("RoCe/FJvoYSi6rSsMQ5D8UU9QirCB9MZXjC6wuYDyhc="),
      bicCode                    = Some("FOJFkUjXAjPmZTcK4WRCyw=="),
      bankAccountCurrencyCode    = Some("EUR"),
      businessActivityCode2      = Some("4477"),
      businessActivityCode3      = Some("2233"),
      cipherText                 = Some("badd2539694a0b3fd547b7cfcb77eefe"),
      encryptionStatus           = Some("M"),
      updateSequenceNumber       = 30
    )
    val updateAppDetailsResponse: UpdateApplicationDetailsResponse = UpdateApplicationDetailsResponse(32)

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

    val purchaseDetailsRequest: GetPurchaseDetailsRequest = GetPurchaseDetailsRequest(applicationId = 123456, itemNumber = 4)

    val purchaseDetailsResponse: GetPurchaseDetailsResponse = GetPurchaseDetailsResponse(
      goodsDescriptionCode       = "1",
      goodsDescriptionSubCode    = Some("1.1"),
      goodsDescriptionText       = Some("Fuel"),
      simplifiedInvoiceIndicator = Some("N"),
      supplierName               = Some("Supplier Ltd"),
      supplierAddressLine1       = Some("1 High Street"),
      supplierAddressLine2       = Some("Riga"),
      supplierAddressLine3       = None,
      supplierVatNumber          = Some("LV40003567907"),
      supplierTaxIdentifier      = None,
      invoiceDate                = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
      invoiceNumber              = Some("INV-001"),
      currencyCode               = Some("EUR"),
      taxableAmount              = Some(BigDecimal("100.50")),
      vatAmount                  = Some(BigDecimal("21.10")),
      deductibleVatAmount        = Some(BigDecimal("21.10")),
      updateSequenceNumber       = 1
    )

    val importationDetailsResponse: GetImportationDetailsResponse = GetImportationDetailsResponse(
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
      taxableAmount               = Some(BigDecimal(1000)),
      vatAmount                   = Some(BigDecimal(99)),
      deductibleVatAmount         = Some(BigDecimal(40)),
      updateSequenceNumber        = 1,
      periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    val vrnCountRequest: SupplierVrnCountRequest = SupplierVrnCountRequest(
      applicationId = 133,
      itemNumber    = 4,
      vatNumber     = "500000881",
      invoiceNumber = "a444"
    )
    val vrnCountResponse: SupplierVrnCountResponse = SupplierVrnCountResponse(duplicateCount = 1)

    val deletePurchaseRequest: DeletePurchaseRequest = DeletePurchaseRequest(applicationId = 404, itemNumber = 4, updateSequenceNumber = 1)

    val sadReferenceCountRequest: SadReferenceCountRequest = SadReferenceCountRequest(
      applicationId      = 133,
      itemNumber         = 4,
      sadReferenceNumber = "500000881"
    )
    val sadReferenceCountResponse: SadReferenceCountResponse = SadReferenceCountResponse(duplicateCount = 1)
  }

}
