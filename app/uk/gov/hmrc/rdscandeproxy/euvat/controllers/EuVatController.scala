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
import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.{AddPurchaseRequest, ApplicationRequest, LatestApplicationRequest, SupplierTaxIdentifierCountRequest}
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.ApplicationResponse
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.DuplicateCountResponse
import uk.gov.hmrc.rdscandeproxy.euvat.services.EuVatService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EuVatController @Inject() (authorise: AuthAction, euVatService: EuVatService, cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def getLatestApplications: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson
        .flatMap(_.asOpt[LatestApplicationRequest])
        .map { latestApplicationRequest =>
          euVatService
            .getLatestApplications(latestApplicationRequest)
            .map { response =>
              logger.info(s"Latest applications retrieved for VRN: ${latestApplicationRequest.applicantVatRegNumber}")
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while retrieving latest applications from oracle database", ex)
              InternalServerError("Failed to retrieve latest applications")
            }
        }
        .getOrElse {
          logger.warn("Invalid request body for getLatestApplications")
          Future.successful(BadRequest("Invalid request body"))
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

}
