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

package uk.gov.hmrc.rdscandeproxy.euvat.services

import uk.gov.hmrc.rdscandeproxy.euvat.models.requests.*
import uk.gov.hmrc.rdscandeproxy.euvat.models.responses.*
import uk.gov.hmrc.rdscandeproxy.euvat.repositories.EuVatCandeRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EuVatService @Inject() (euvatCandeRepository: EuVatCandeRepository)(implicit ec: ExecutionContext) {

  def getLatestApplications(request: LatestApplicationRequest): Future[LatestApplicationResponse] = {
    euvatCandeRepository.getLatestApplications(request)
  }

  def addApplication(applicationRequest: ApplicationRequest, vrn: String): Future[ApplicationResponse] =
    euvatCandeRepository.addApplication(applicationRequest, vrn)

  def addPurchase(purchaseRequest: AddPurchaseRequest): Future[AddPurchaseResponse] =
    euvatCandeRepository.addPurchase(purchaseRequest)

  def addImport(importRequest: AddImportRequest): Future[AddImportResponse] =
    euvatCandeRepository.addImportation(importRequest)

  def getPurchaseDetails(request: GetPurchaseDetailsRequest): Future[Option[GetPurchaseDetailsResponse]] =
    euvatCandeRepository.getPurchaseDetails(request)

  def getImportationDetails(request: GetImportationDetailsRequest): Future[Option[GetImportationDetailsResponse]] =
    euvatCandeRepository.getImportationDetails(request)

  def getSupplierVrnCount(request: SupplierVrnCountRequest): Future[SupplierVrnCountResponse] =
    euvatCandeRepository.getSupplierVrnCount(request)

  def getSupplierTaxIdentifierDuplicateCount(request: SupplierTaxIdentifierCountRequest): Future[Int] =
    euvatCandeRepository.getSupplierTaxIdentifierDuplicateCount(request)

  def deleteApplication(request: uk.gov.hmrc.rdscandeproxy.euvat.models.requests.DeleteApplicationRequest): Future[Unit] =
    euvatCandeRepository.deleteApplication(request)

  def updatePurchaseDetails(request: UpdatePurchaseDetailsRequest): Future[UpdatePurchaseDetailsResponse] =
    euvatCandeRepository
      .updatePurchaseDetails(request)
      .map(UpdatePurchaseDetailsResponse(_))

  def getPurchaseImportList(request: PurchaseImportListRequest): Future[PurchaseImportListResponse] =
    euvatCandeRepository.getPurchaseImportList(request)

  def updateApplicationDetails(request: UpdateApplicationDetailsRequest): Future[UpdateApplicationDetailsResponse] =
    euvatCandeRepository
      .updateApplicationDetails(request)
      .map(UpdateApplicationDetailsResponse(_))

  def deletePurchase(request: DeletePurchaseRequest): Future[DeletePurchaseResponse] =
    euvatCandeRepository
      .deletePurchase(request)
      .map(DeletePurchaseResponse(_))

  def getSadReferenceCount(request: SadReferenceCountRequest): Future[SadReferenceCountResponse] =
    euvatCandeRepository.getSadReferenceCount(request)

  def deleteImport(request: DeleteImportRequest): Future[DeleteImportResponse] =
    euvatCandeRepository
      .deleteImport(request)
      .map(DeleteImportResponse(_))

}
