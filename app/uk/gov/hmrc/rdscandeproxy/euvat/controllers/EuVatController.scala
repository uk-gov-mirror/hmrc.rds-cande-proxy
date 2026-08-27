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

package uk.gov.hmrc.rdscandeproxy.euvat.controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.rdscandeproxy.euvat.actions.AuthAction
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.*
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.*
import uk.gov.hmrc.rdscandeproxy.euvat.services.EuVatService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EuVatController @Inject() (authorise: AuthAction, euVatService: EuVatService, cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def getLatestApplications: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[LatestApplicationRequest]) match {
        case None =>
          logger.warn("Invalid JSON for LatestApplicationRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(latestApplicationRequest) =>
          if (latestApplicationRequest.applicantVatRegNumber.isEmpty) {
            logger.warn("Invalid JSON: applicantVatRegNumber cannot be empty")
            Future.successful(BadRequest("Invalid request: applicantVatRegNumber"))
          } else {
            euVatService
              .getLatestApplications(latestApplicationRequest)
              .map(response => Ok(Json.toJson(response)))
              .recover { case ex: Exception =>
                logger.error("Error while retrieving latest applications from oracle database", ex)
                InternalServerError("Failed to create latest applications")
              }
          }
      }
    }

  def addApplication: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[ApplicationRequest]) match {
        case None =>
          logger.warn("Invalid JSON for ApplicationRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(appRequest) =>
          euVatService
            .addApplication(appRequest, request.identifierValue)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while creating the refund application", ex)
              InternalServerError("Failed to create refund application")
            }
      }
    }

  def addPurchase: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[AddPurchaseRequest]) match {
        case None =>
          logger.warn("Invalid JSON for AddPurchaseRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(purchaseRequest) =>
          euVatService
            .addPurchase(purchaseRequest)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while adding the purchase", ex)
              InternalServerError("Failed to add purchase")
            }
      }
    }

  def getSupplierVrnCount: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson
        .flatMap(_.asOpt[SupplierVrnCountRequest])
        .map { vrnCountRequest =>
          euVatService
            .getSupplierVrnCount(vrnCountRequest)
            .map { response =>
              logger.info(s"Supplier VRN count retrieved for applicationId: ${vrnCountRequest.applicationId}")
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while retrieving supplier VRN count from oracle database", ex)
              InternalServerError("Failed to retrieve supplier VRN count")
            }
        }
        .getOrElse {
          logger.warn("Invalid request body for getSupplierVrnCount")
          Future.successful(BadRequest("Invalid request body"))
        }
    }

  def getSupplierTaxIdentifierCount: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[SupplierTaxIdentifierCountRequest]) match {
        case None =>
          logger.warn("Invalid JSON for SupplierTaxIdentifierCountRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .getSupplierTaxIdentifierDuplicateCount(req)
            .map { count =>
              Ok(Json.toJson(DuplicateCountResponse(count)))
            }
            .recover { case ex: Exception =>
              logger.error("Error while retrieving duplicate count", ex)
              InternalServerError("Failed to retrieve duplicate count")
            }
      }
    }

  def updatePurchaseDetails: Action[AnyContent] =
    authorise.async { implicit request =>
      val requestJson = request.body.asJson.map(Json.stringify).getOrElse("No JSON")
      logger.info(s"updatePurchaseDetails request body: $requestJson")

      request.body.asJson.flatMap(_.asOpt[UpdatePurchaseDetailsRequest]) match {
        case None =>
          logger.warn("Invalid JSON for UpdatePurchaseDetailsRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .updatePurchaseDetails(req)
            .map { response =>
              val responseJson = Json.stringify(Json.toJson(response))
              logger.info(s"updatePurchaseDetails response body: $responseJson")
              Ok(Json.toJson(response))
            }
            .recover {
              case sqlEx: java.sql.SQLException if sqlEx.getErrorCode == 20000 =>
                logger.warn("Concurrent update detected while updating purchase details", sqlEx)
                Conflict("Refund application already updated by another session")
              case ex: Exception =>
                logger.error("Error while updating purchase details", ex)
                InternalServerError("Failed to update purchase details")
            }
      }
    }

}
