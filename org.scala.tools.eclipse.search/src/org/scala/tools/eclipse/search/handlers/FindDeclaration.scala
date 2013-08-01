package org.scala.tools.eclipse.search.handlers

import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.util.Utils.WithAsInstanceOfOpt

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.search.ui.ISearchQuery
import org.eclipse.search.ui.ISearchResult
import org.eclipse.search.ui.NewSearchUI
import org.eclipse.search.ui.text.Match
import org.eclipse.ui.handlers.HandlerUtil
import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.ErrorReporter
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.UIUtil
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.searching.SearchPresentationCompiler
import org.scala.tools.eclipse.search.ui.DialogErrorReporter
import org.scala.tools.eclipse.search.ui.SearchResult

class FindDeclaration extends AbstractHandler with HasLogger {

  private val finder: Finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  override def execute(event: ExecutionEvent): Object = {
    logger.debug("start find declaration search")
    for {
      editor      <- Option(HandlerUtil.getActiveEditor(event)) onEmpty reporter.reportError("An editor has to be active")
      scalaEditor <- editor.asInstanceOfOpt[ScalaSourceFileEditor] onEmpty reporter.reportError("Active editor wasn't a Scala editor")
      selection   <- UIUtil.getSelection(scalaEditor) onEmpty reporter.reportError("You need to have a selection")
    } {
      scalaEditor.getInteractiveCompilationUnit.withSourceFile { (_, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        val loc = Location(scalaEditor.getInteractiveCompilationUnit, selection.getOffset())
        spc.entityAt(loc) map { entity =>
          if (spc.canFindReferences(entity)) {
            startSearch(entity)
          } else reporter.reportError("Sorry, that kind of entity isn't supported yet.")
        } getOrElse(reporter.reportError("Couldn't recognize the enity of the selection"))
      }()
    }
    logger.debug("finish find declaration search")
    null
  }

  private def startSearch(entity: Entity): Unit = {
    NewSearchUI.runQueryInBackground(new ISearchQuery() {

      val sr = new SearchResult(this)

      override def canRerun(): Boolean = false
      override def canRunInBackground(): Boolean = true

      override def getLabel: String =
        s"'${entity.name}'"

      override def run(monitor: IProgressMonitor): IStatus = {
        finder.findDeclaration(entity, monitor)(
          handler = {
            case h @ Certain(hit) =>
              sr.addMatch(new Match(h, hit.offset, hit.word.length))
          },
          errorHandler = fail => {
            logger.debug(s"Got an error ${fail}")
          })
        Status.OK_STATUS
      }
      override def getSearchResult(): ISearchResult = sr
    })
  }
}