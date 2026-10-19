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

package uk.gov.hmrc.rdscandeproxy.euvat.models.responses

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class DeleteImportResponseSpec extends AnyWordSpec with Matchers {

  "DeleteImportResponse JSON format" should {

    "serialize to JSON correctly" in {
      val response = DeleteImportResponse(updateSequenceNumber = 2)

      Json.toJson(response) shouldBe Json.obj(
        "updateSequenceNumber" -> 2
      )
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "updateSequenceNumber" -> 2
      )

      json.as[DeleteImportResponse] shouldBe DeleteImportResponse(updateSequenceNumber = 2)
    }
  }
}
