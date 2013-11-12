package models

import play.api.libs.json.Json
import library.{Dress, Redis}
import org.apache.commons.lang3.{StringUtils, RandomStringUtils}

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

/**
 * Proposal
 *
 * Author: nicolas
 * Created: 12/10/2013 15:19
 */
case class ProposalType(id: String, label: String)

object ProposalType {
  implicit val proposalTypeFormat = Json.format[ProposalType]

  val CONF = ProposalType("conf", "conf.label")
  val UNI = ProposalType("uni", "uni.label")
  val TIA = ProposalType("tia", "tia.label")
  val LAB = ProposalType("lab", "lab.label")
  val QUICK = ProposalType("quick", "quick.label")
  val BOF = ProposalType("bof", "bof.label")
  val AMD = ProposalType("amd", "amd.label")
  val KEY = ProposalType("key", "key.label")
  val OTHER = ProposalType("other", "other.label")

  val all = List(CONF, UNI, TIA, LAB, QUICK, BOF)

  val allAsId = all.map(a => (a.id, a.label)).toSeq.sorted

  def parse(proposalType: String): ProposalType = {
    proposalType match {
      case "conf" => CONF
      case "uni" => UNI
      case "tia" => TIA
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "amd" => AMD
      case "key" => KEY
      case other => OTHER
    }
  }
}


case class ProposalState(code: String)

object ProposalState {

  implicit val proposalTypeState = Json.format[ProposalState]

  val DRAFT = ProposalState("draft")
  val SUBMITTED = ProposalState("submitted")
  val DELETED = ProposalState("deleted")
  val APPROVED = ProposalState("approved")
  val REJECTED = ProposalState("rejected")
  val ACCEPTED = ProposalState("accepted")
  val DECLINED = ProposalState("declined")
  val BACKUP = ProposalState("backup")
  val UNKNOWN = ProposalState("unknown")


  val all = List(
    DRAFT,
    SUBMITTED,
    DELETED,
    APPROVED,
    REJECTED,
    ACCEPTED,
    DECLINED,
    BACKUP,
    UNKNOWN
  )

  val allAsCode = all.map(_.code)
}

// A proposal
case class Proposal(id: Option[String], event: String, code: String, lang: String, title: String,
                    mainSpeaker: String, secondarySpeaker: Option[String], otherSpeakers: List[String], talkType: ProposalType, audienceLevel: String, summary: String,
                    privateMessage: String, state: ProposalState, sponsorTalk: Boolean = false, track: Track)

object Proposal {

  implicit val proposalFormat = Json.format[Proposal]

  val langs = Seq(("en", "English"), ("fr", "French"))

  val audienceLevels = Seq(("novice", "Novice"), ("intermediate", "Intermediate"), ("expert", "Expert"))

  def save(creator: String, proposal: Proposal, proposalState: ProposalState) = Redis.pool.withClient {
    client =>
      val json = Json.toJson(proposal).toString

      // TX
      val tx = client.multi()
      tx.hset("Proposals", proposal.id.get, json)
      tx.sadd("Proposals:ByAuthor:" + creator, proposal.id.get)
      tx.exec()

      Event.storeEvent(Event("proposal", creator, "Updated or created proposal " + proposal.id.get + " with title " + StringUtils.abbreviate(proposal.title, 80)))

      changeTrack(creator, proposal)

      changeProposalState(creator, proposal.id.get, proposalState)
  }

  val proposalForm = Form(mapping(
    "id" -> optional(text),
    "lang" -> text,
    "title" -> nonEmptyText(maxLength = 125),
    "mainSpeaker" -> nonEmptyText,
    "secondarySpeaker" -> optional(text),
    "otherSpeakers" -> list(text),
    "talkType" -> nonEmptyText,
    "audienceLevel" -> text,
    "summary" -> nonEmptyText(maxLength = 2000),
    "privateMessage" -> optional(text(maxLength = 1000)),
    "sponsorTalk" -> boolean,
    "track" -> nonEmptyText
  )(validateNewProposal)(unapplyProposalForm))

  def generateId(): Option[String] = {
    Some(RandomStringUtils.randomAlphabetic(3).toUpperCase + "-" + RandomStringUtils.randomNumeric(3))
  }

  def validateNewProposal(id: Option[String], lang: String, title: String, mainSpeaker: String,
                          secondarySpeaker: Option[String], otherSpeakers: List[String],
                          talkType: String, audienceLevel: String, summary: String, privateMessage: Option[String],
                          sponsorTalk: Boolean, track: String): Proposal = {
    val code = RandomStringUtils.randomAlphabetic(3).toUpperCase + "-" + RandomStringUtils.randomNumeric(3)
    Proposal(id.orElse(generateId()),
      "Devoxx France 2014",
      code,
      lang,
      title,
      mainSpeaker,
      secondarySpeaker,
      otherSpeakers,
      ProposalType.parse(talkType),
      audienceLevel,
      summary,
      StringUtils.trimToEmpty(privateMessage.getOrElse("")),
      ProposalState.UNKNOWN,
      sponsorTalk,
      Track.parse(track)
    )

  }

  def isNew(id: String): Boolean = Redis.pool.withClient {
    client =>
    // Important when we create a new proposal
      client.hexists("Proposals", id) == false
  }

  def unapplyProposalForm(p: Proposal): Option[(Option[String], String, String, String, Option[String], List[String], String, String, String, Option[String],
    Boolean, String)] = {
    Option((p.id, p.lang, p.title, p.mainSpeaker, p.secondarySpeaker, p.otherSpeakers, p.talkType.id, p.audienceLevel, p.summary, Option(p.privateMessage),
      p.sponsorTalk, p.track.id))
  }

  private def changeTrack(owner: String, proposal: Proposal) = Redis.pool.withClient {
    client =>
      val proposalId = proposal.id.get
      // If we change a proposal to a new track, we need to update all the collections
      // On Redis, this is very fast (faster than creating a mongoDB index, by an order of x100)

      // SISMember is a O(1) operation
      val maybeExistingTrack = for (trackId <- Track.allIDs if client.sismember("Proposals:ByTrack:" + trackId, proposalId)) yield trackId

      // Do the operation if and only if we changed the Track
      maybeExistingTrack.filterNot(_ == proposal.track.id).foreach {
        oldTrackId: String =>
        // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("Proposals:ByTrack:" + oldTrackId, "Proposals:ByTrack:" + proposal.track.id, proposalId)
          // And we are able to track this event
          Event.storeEvent(Event("proposal", owner, s"${owner} changed talk's track  with id ${proposalId}  from ${oldTrackId} to ${proposal.track.id}"))
      }
      if (maybeExistingTrack.isEmpty) {
        // SADD is O(N)
        client.sadd("Proposals:ByTrack:" + proposal.track.id, proposalId)
        Event.storeEvent(Event("proposal", owner, s"${owner} posted a new talk (${proposalId}) to ${proposal.track.id}"))
      }

  }

  private def changeProposalState(owner: String, proposalId: String, newState: ProposalState) = Redis.pool.withClient {
    client =>
    // Same kind of operation for the proposalState
      val maybeExistingState = for (state <- ProposalState.allAsCode if client.sismember("Proposals:ByState:" + state, proposalId)) yield state

      // Do the operation on the ProposalState
      maybeExistingState.filterNot(_ == newState.code).foreach {
        stateOld: String =>
        // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("Proposals:ByState:" + stateOld, "Proposals:ByState:" + newState.code, proposalId)
          Event.storeEvent(Event("proposal", owner, s"${owner} changed status of talk ${proposalId} from ${stateOld} to ${newState.code}"))

      }
      if (maybeExistingState.isEmpty) {
        // SADD is O(N)
        client.sadd("Proposals:ByState:" + newState.code, proposalId)
        Event.storeEvent(Event("proposal", owner, s"${owner} posted new talk ${proposalId} with status ${newState.code}"))
      }
  }

  def delete(owner: String, proposalId: String) {
    changeProposalState(owner, proposalId, ProposalState.DELETED)
  }

  def submit(owner: String, proposalId: String) = {
    changeProposalState(owner, proposalId, ProposalState.SUBMITTED)
  }

  def draft(owner: String, proposalId: String) = {
    changeProposalState(owner, proposalId, ProposalState.DRAFT)
  }

  private def loadProposalsByState(email: String, proposalState: ProposalState): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds: Set[String] = client.sinter(s"Proposals:ByState:${proposalState.code}", s"Proposals:ByAuthor:${email}")
      loadProposalByIDs(allProposalIds, proposalState)
  }

  // Special function that has to be executed with an implicit client
  def loadProposalByIDs(allProposalIds: Set[String], proposalState: ProposalState)(implicit client: Dress.Wrap): List[Proposal] = {
    client.hmget("Proposals", allProposalIds).flatMap {
      proposalJson: String =>
        Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = proposalState))
    }.sortBy(_.title)
  }

  def allMyDraftProposals(email: String): List[Proposal] = {
    loadProposalsByState(email, ProposalState.DRAFT).sortBy(_.title)
  }

  def allMyDeletedProposals(email: String): List[Proposal] = {
    loadProposalsByState(email, ProposalState.DELETED).sortBy(_.title)
  }

  def allMySubmittedProposals(email: String): List[Proposal] = {
    loadProposalsByState(email, ProposalState.SUBMITTED).sortBy(_.title)
  }

  def allMyDraftAndSubmittedProposals(email: String): List[Proposal] = {
    val allDrafts = allMyDraftProposals(email)
    val allSubmitted = allMySubmittedProposals(email)
    (allDrafts ++ allSubmitted).sortBy(_.title)
  }

  def givesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
    proposalType match {
      case ProposalType.CONF => true
      case ProposalType.KEY => true
      case ProposalType.LAB => true
      case ProposalType.UNI => true
      case ProposalType.TIA => true
      case other => false
    }
  }

  val proposalSpeakerForm = Form(tuple(
    "mainSpeaker" -> nonEmptyText,
    "secondarySpeaker" -> optional(text),
    "otherSpeakers" -> list(text)
  ))

  def findById(proposalId: String): Option[Proposal] = Redis.pool.withClient {
    client =>
      for (proposalJson <- client.hget("Proposals", proposalId);
           proposal <- Json.parse(proposalJson).asOpt[Proposal];
           realState <- findProposalState(proposal.id.get)) yield {
        proposal.copy(state = realState)
      }

  }

  def findProposalState(proposalId: String): Option[ProposalState] = Redis.pool.withClient {
    client =>
      // I use a for-comprehension to check each of the Set (O(1) operation)
      // when I have found what is the current state, then I stop and I return a Left that here, indicates a success
      // Note that the common behavioir for an Either is to indicate failure as a Left and Success as a Right,
      // Here I do the opposite for performance reasons. NMA.
      // This code retrieves the proposalState in less than 4ms so it is really efficient.
      val thisProposalState = for (
        isNotSubmitted <- checkIsNotMember(client, ProposalState.SUBMITTED, proposalId).toRight(ProposalState.SUBMITTED).right;
        isNotDraft <- checkIsNotMember(client, ProposalState.DRAFT, proposalId).toRight(ProposalState.DRAFT).right;
        isNotApproved <- checkIsNotMember(client, ProposalState.APPROVED, proposalId).toRight(ProposalState.APPROVED).right;
        isNotDeleted <- checkIsNotMember(client, ProposalState.DELETED, proposalId).toRight(ProposalState.DELETED).right;
        isNotDeclined <- checkIsNotMember(client, ProposalState.DECLINED, proposalId).toRight(ProposalState.DECLINED).right;
        isNotRejected <- checkIsNotMember(client, ProposalState.REJECTED, proposalId).toRight(ProposalState.REJECTED).right;
        isNotAccepted <- checkIsNotMember(client, ProposalState.ACCEPTED, proposalId).toRight(ProposalState.ACCEPTED).right;
        isNotBackup <- checkIsNotMember(client, ProposalState.BACKUP, proposalId).toRight(ProposalState.BACKUP).right
      ) yield ProposalState.UNKNOWN // If we reach this code, we could not find what was the proposal state

      thisProposalState.fold(foundProposalState => Some(foundProposalState), notFound => {
        play.Logger.warn(s"Could not find proposal state for $proposalId")
        None
      })
  }

  private def checkIsNotMember(client: Dress.Wrap, state: ProposalState, proposalId: String): Option[String] = {
    client.sismember("Proposals:ByState:" + state.code, proposalId) match {
      case java.lang.Boolean.FALSE => Some("notMember")
      case other => None
    }
  }
}
