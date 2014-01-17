package com.typesafe.sbtrc

import _root_.sbt._
import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope
import sbt.Aggregation.KeyValue
import sbt.complete.DefaultParsers
import sbt.Load.BuildStructure
import SbtCustomHacks._

// TODO - We may want to remove this class, or 90% of it.
object SbtUtil {

  def extractWithRef(state: State): (Extracted, ProjectRef) = {
    val extracted = Project.extract(state)
    (Extracted(extracted.structure, extracted.session, extracted.currentRef)(showFullKey(state)), extracted.currentRef)
  }

  def extract(state: State): Extracted = {
    extractWithRef(state)._1
  }

  def getSettingValue[T](key: sbt.ScopedKey[T], state: State): T = {
    extract(state).get(sbt.SettingKey(key.key) in key.scope)
  }

  def runInputTask[T](key: sbt.ScopedKey[T], state: State, args: String): State = {
    val extracted = extract(state)
    implicit val display = Project.showContextKey(state)
    val it = extracted.get(SettingKey(key.key) in key.scope)
    val keyValues = KeyValue(key, it) :: Nil

    val parser = Aggregation.evaluatingParser(state, extracted.structure, show = dontShowAggregate)(keyValues)
    // we put a space in front of the args because the parsers expect
    // *everything* after the task name it seems
    DefaultParsers.parse(" " + args, parser) match {
      case Left(message) =>
        throw new Exception("Failed to run task: " + display(key) + ": " + message)
      case Right(f) =>
        f()
    }
  }

  def runCommand(command: String, state: State): State = {
    sbt.Command.process(command, state)
  }

  /** A helper method to ensure that settings we're appending are scoped according to the current project ref. */
  def makeAppendSettings(settings: Seq[Setting[_]], inProject: ProjectRef, extracted: Extracted) = {
    // transforms This scopes in 'settings' to be the desired project
    val appendSettings = Load.transformSettings(Load.projectScope(inProject), inProject.build, extracted.rootProject, settings)
    appendSettings
  }

  /** Reloads an sbt build with the given settings being appended to the current session. */
  def reloadWithAppended(state: State, appendSettings: Seq[sbt.Setting[_]]): State = {
    // reloads with appended settings.
    val session = Project.session(state)
    //val structure = Project.structure(state)
    //implicit val display = Project.showContextKey(state)
    // When we reload, make sure we keep all reapplied settings...
    //val newStructure = Load.reapply(session.mergeSettings ++ appendSettings, structure)
    val newSession = session.appendRaw(appendSettings)
    // updates various aspects of State based on the new settings
    // and returns the updated State
    SessionSettings.reapply(newSession, state)
  }
}
