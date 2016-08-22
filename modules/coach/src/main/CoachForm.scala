package lila.coach

import org.joda.time.DateTime
import play.api.data._
import play.api.data.format.Formatter
import play.api.data.Forms._

object CoachForm {

  def edit(coach: Coach) = Form(mapping(
    "hourlyRate" -> optional(number(min = 5, max = 500)),
    "available" -> optional(number),
    "profile" -> profileMapping
  )(Data.apply)(Data.unapply)) fill Data(
    hourlyRate = coach.hourlyRate.map(_.value),
    available = coach.available.value option 1,
    profile = coach.profile)

  case class Data(
      hourlyRate: Option[Int],
      available: Option[Int],
      profile: CoachProfile) {

    def apply(coach: Coach) = coach.copy(
      hourlyRate = hourlyRate.map(_ * 100) map Coach.Cents.apply,
      available = Coach.Available(available.isDefined),
      profile = profile,
      updatedAt = DateTime.now)
  }

  private def profileMapping = mapping(
    "headline" -> optional(nonEmptyText(minLength = 5, maxLength = 140)),
    "description" -> optional(markdown),
    "playingExperience" -> optional(markdown),
    "teachingExperience" -> optional(markdown),
    "otherExperience" -> optional(markdown),
    "skills" -> optional(markdown),
    "methodology" -> optional(markdown)
  )(CoachProfile.apply)(CoachProfile.unapply)

  import CoachProfile.Markdown

  private def markdown = of[Markdown]
  private implicit def markdownFormat: Formatter[Markdown] = new Formatter[Markdown] {
    def bind(key: String, data: Map[String, String]) =
      data.get(key).map(Markdown.apply).toRight(Seq(FormError(key, "error.required", Nil)))
    def unbind(key: String, value: Markdown) = Map(key -> value.value)
  }
}
