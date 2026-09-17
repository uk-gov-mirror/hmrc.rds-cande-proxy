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

  def getPurchaseDetails(request: GetPurchaseDetailsRequest): Future[Option[GetPurchaseDetailsResponse]] =
    euvatCandeRepository.getPurchaseDetails(request)

  def getSupplierVrnCount(request: SupplierVrnCountRequest): Future[SupplierVrnCountResponse] =
    euvatCandeRepository.getSupplierVrnCount(request)

  def getSupplierTaxIdentifierDuplicateCount(request: SupplierTaxIdentifierCountRequest): Future[Int] =
    euvatCandeRepository.getSupplierTaxIdentifierDuplicateCount(request)

  def updatePurchaseDetails(request: UpdatePurchaseDetailsRequest): Future[UpdatePurchaseDetailsResponse] =
    euvatCandeRepository
      .updatePurchaseDetails(request)
      .map(UpdatePurchaseDetailsResponse(_))

}
