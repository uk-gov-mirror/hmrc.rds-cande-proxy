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
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.*
import uk.gov.hmrc.rdscandeproxy.euvat.base.SpecBase
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.{AddPurchaseRequest, AddPurchaseResponse, ApplicationRequest, LatestApplicationRequest}
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.LatestApplicationResponse
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.ApplicationResponse
import uk.gov.hmrc.rdscandeproxy.euvat.services.EuVatService

import java.time.LocalDateTime
import scala.concurrent.Future

class EuVatControllerSpec extends SpecBase with MockitoSugar {

  "EuVatController" - {

    "getLatestApplications" - {
      "return 200 with JSON when service returns latest applications" in new SetUp {
        when(mockEuVatService.getLatestApplications(any()))
          .thenReturn(Future.successful(sampleResponse))

        val result: Future[Result] = controller.getLatestApplications()(
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(sampleRequest))
        )

        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(sampleResponse)
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
          fakeRequest.withMethod("POST").withJsonBody(Json.toJson(sampleRequest))
        )

        status(result) shouldBe INTERNAL_SERVER_ERROR
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

    "getSupplierTaxIdentifierCount" - {
      "return 200 with JSON when service returns count" in new SetUp {
        when(mockEuVatService.getSupplierTaxIdentifierDuplicateCount(any()))
          .thenReturn(Future.successful(4))

        val json = Json.obj(
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

        val json = Json.obj(
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

  }

  private class SetUp {
    val mockEuVatService: EuVatService = mock[EuVatService]

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

    val controller = new EuVatController(fakeAuthAction, mockEuVatService, cc)

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

  }

}
