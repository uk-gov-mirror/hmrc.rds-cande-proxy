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

  def deleteApplication: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson
        .flatMap(_.asOpt[uk.gov.hmrc.rdscandeproxy.euvat.models.requests.DeleteApplicationRequest]) match {
        case None =>
          logger.warn("Invalid JSON for DeleteApplicationRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .deleteApplication(req)
            .map(_ => Ok)
            .recover { case ex: Exception =>
              logger.error("Error while deleting the refund application", ex)
              InternalServerError("Failed to delete refund application")
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

  def addImport: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[AddImportRequest]) match {
        case None =>
          logger.warn("Invalid JSON for AddImportRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(importRequest) =>
          euVatService
            .addImport(importRequest)
            .map(response => Ok(Json.toJson(response)))
            .recover { case ex: Exception =>
              logger.error("Error while adding the import", ex)
              InternalServerError("Failed to add import")
            }
      }
    }

  def getPurchaseDetails: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[GetPurchaseDetailsRequest]) match {
        case None =>
          logger.warn("Invalid JSON for GetPurchaseDetailsRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(detailsRequest) =>
          euVatService
            .getPurchaseDetails(detailsRequest)
            .map {
              case Some(response) => Ok(Json.toJson(response))
              case None =>
                logger.error(s"No purchase record for applicationId ${detailsRequest.applicationId} itemNumber ${detailsRequest.itemNumber}")
                InternalServerError("Failed to retrieve purchase details")
            }
            .recover { case ex: Exception =>
              logger.error("Error while retrieving the purchase details", ex)
              InternalServerError("Failed to retrieve purchase details")
            }
      }
    }

  def getImportationDetails(applicationId: Long, itemNumber: Int): Action[AnyContent] =
    authorise.async { implicit request =>
      euVatService
        .getImportationDetails(GetImportationDetailsRequest(applicationId, itemNumber))
        .map {
          case Some(response) => Ok(Json.toJson(response))
          case None =>
            logger.error(s"No importation record for applicationId $applicationId itemNumber $itemNumber")
            InternalServerError("Failed to retrieve importation details")
        }
        .recover { case ex: Exception =>
          logger.error("Error while retrieving the importation details", ex)
          InternalServerError("Failed to retrieve importation details")
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
      request.body.asJson.flatMap(_.asOpt[UpdatePurchaseDetailsRequest]) match {
        case None =>
          logger.warn("Invalid JSON for UpdatePurchaseDetailsRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .updatePurchaseDetails(req)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while updating purchase details", ex)
              InternalServerError("Failed to update purchase details")
            }
      }
    }

  def getPurchaseImportList: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[PurchaseImportListRequest]) match {
        case None =>
          logger.warn("Invalid JSON for PurchaseImportListRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .getPurchaseImportList(req)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while retrieving purchase import list", ex)
              InternalServerError("Failed to retrieve purchase import list")
            }
      }
    }

  def updateApplicationDetails: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[UpdateApplicationDetailsRequest]) match {
        case None =>
          logger.warn("Invalid JSON for UpdateApplicationDetailsRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .updateApplicationDetails(req)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while updating application details", ex)
              InternalServerError("Failed to update application details")
            }
      }
    }

  def deletePurchase: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[DeletePurchaseRequest]) match {
        case None =>
          logger.warn("Invalid JSON for DeletePurchaseRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .deletePurchase(req)
            .map(response => Ok(Json.toJson(response)))
            .recover { case ex: Exception =>
              logger.error("Error while deleting purchase", ex)
              InternalServerError("Failed to delete purchase")
            }
      }
    }

  def getSadReferenceCount: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson
        .flatMap(_.asOpt[SadReferenceCountRequest])
        .map { sadReferenceCountRequest =>
          euVatService
            .getSadReferenceCount(sadReferenceCountRequest)
            .map { response =>
              logger.info(s"SAD reference count retrieved for applicationId: ${sadReferenceCountRequest.applicationId}")
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while retrieving SAD reference count from oracle database", ex)
              InternalServerError("Failed to retrieve SAD reference count")
            }
        }
        .getOrElse {
          logger.warn("Invalid request body for getSadReferenceCount")
          Future.successful(BadRequest("Invalid request body"))
        }
    }

  def deleteImport: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[DeleteImportRequest]) match {
        case None =>
          logger.warn("Invalid JSON for DeleteImportRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(req) =>
          euVatService
            .deleteImport(req)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while deleting the import", ex)
              InternalServerError("Failed to delete import")
            }
      }
    }
}
