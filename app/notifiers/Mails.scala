/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package notifiers


import com.typesafe.plugin._
import play.api.Play.current
import org.joda.time.DateTime
import models.{Proposal, Issue}

/**
 * Sends all emails
 *
 * Author: nmartignole
 * Created: 04/10/2013 15:56
 */

object Mails {

  def sendResetPasswordLink(email: String, resetUrl: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("You asked to reset your Devoxx France speaker's password at "+new DateTime().toString("HH:mm dd/MM"))
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(views.txt.Mails.sendResetLink(resetUrl).toString(), views.html.Mails.sendResetLink(resetUrl).toString)
  }

  def sendAccessCode(email: String, code: String) = {
      val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
      emailer.setSubject("Your Devoxx France speaker's access code")
      emailer.addFrom("program@devoxx.fr")
      emailer.addRecipient(email)
      emailer.setCharset("utf-8")
      emailer.send(
        views.txt.Mails.sendAccessCode(email, code).toString(),
         views.html.Mails.sendAccessCode(email, code).toString
      )
    }


  def sendWeCreatedAnAccountForYou(email: String, firstname: String, tempPassword: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("Welcome to Devoxx France ! ")
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(views.txt.Mails.sendWeCreatedAnAccountForYou(firstname, email, tempPassword).toString(), views.html.Mails.sendWeCreatedAnAccountForYou(firstname, email, tempPassword).toString)
  }

  def sendValidateYourEmail(email:String, validationLink:String)={
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("Devoxx France, please validate your email address now")
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendValidateYourEmail(validationLink).toString(),
      views.html.Mails.sendValidateYourEmail(validationLink).toString()
    )
  }

  def sendBugReport(bugReport:Issue)={
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
        emailer.setSubject("New issue reported on CFP web site")
        emailer.addFrom("program@devoxx.fr")
        emailer.addCc(bugReport.reportedBy)
        emailer.addRecipient("nicolas.martignole@devoxx.fr")
        emailer.setCharset("utf-8")
        emailer.send(
          views.html.Mails.sendBugReport(bugReport).toString(),
          views.html.Mails.sendBugReport(bugReport).toString()
        )
  }

  def sendMessageToSpeakers(fromName:String, fromEmail:String,  proposal:Proposal, msg:String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
         emailer.setSubject("New question about your presentation ${proposal.id.get} for Devoxx France 2014")
         emailer.addFrom("program@devoxx.fr")
         emailer.addRecipient(proposal.mainSpeaker)
         proposal.secondarySpeaker.map(email=> emailer.addCc(email))
         proposal.otherSpeakers.foreach(email => emailer.addCc(email))
         emailer.setCharset("utf-8")
         emailer.send(
           views.txt.Mails.sendMessageToSpeaker(fromName, proposal, msg).toString(),
           views.html.Mails.sendMessageToSpeaker(fromName, proposal, msg).toString()
         )

  }
}