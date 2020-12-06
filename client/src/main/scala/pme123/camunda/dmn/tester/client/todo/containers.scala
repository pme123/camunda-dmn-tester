package pme123.camunda.dmn.tester.client.todo

import autowire.{clientCallable, _}
import boopickle.Default._
import pme123.camunda.dmn.tester.client.services.AjaxClient
import pme123.camunda.dmn.tester.client.todo.components.{AddTodoForm, TList}
import pme123.camunda.dmn.tester.shared
import pme123.camunda.dmn.tester.shared.{DmnApi, DmnConfig}
import slinky.core.FunctionalComponent
import slinky.core.facade.Hooks.{useEffect, useState}
import typings.antd.antdStrings
import typings.antd.antdStrings.{center, middle}
import typings.antd.components._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.util.{Failure, Success}

@JSImport("resources/Todo.css", JSImport.Default)
@js.native
object TodoCSS extends js.Object

object containers {
  private val css = TodoCSS

  val TodoContainer: FunctionalComponent[Unit] = FunctionalComponent[Unit] {
    _ =>
      val (error, setError) = useState[Option[String]](None)
      val (isLoaded, setIsLoaded) = useState(false)
      val (todos, setItems) = useState(Seq.empty[DmnConfig])

      // Note: the empty deps array [] means
      // this useEffect will run once
      useEffect(        () =>
          AjaxClient[DmnApi].getConfigs(Seq("dmnTester", "dmn-configs")).call().onComplete {
            case Success(todos) =>
              setIsLoaded(true)
              setItems(todos)
            case Failure(ex) =>
              setIsLoaded(true)
              setError(Some(ex.toString))
          },
        Seq.empty
      )

      lazy val handleFormSubmit =
        (todo: DmnConfig) =>
          /*AjaxClient[DmnApi].updateTodo(todo).call().foreach { todos =>
            setItems(todos)
          }*/  ()

      lazy val handleTodoToggle =
        (todo: DmnConfig) =>
        /*  AjaxClient[DmnApi].updateTodo(todo.copy(completed = !todo.completed)).call().foreach { todos =>
            setItems(todos)
          }*/  ()
      lazy val handleRemoveTodo =
        (todo: DmnConfig) =>
       /*   AjaxClient[DmnApi].deleteTodo(todo.id.get).call().foreach { todos =>
            setItems(todos)
          }*/  ()
      Row
        // .gutter(20) //[o, 20] ?
        .justify(center)
        .className("todos-container")
        .align(middle)(
          Col
            .xs(23)
            .sm(23)
            .md(21)
            .lg(20)
            .xl(18)(
              PageHeader
                .title("Add Todo")
                .subTitle(
                  "To add a todo, just fill the form below and click in add todo."
                )
            ),
          Col
            .xs(23)
            .sm(23)
            .md(21)
            .lg(20)
            .xl(18)(
              Card
                .title("Create a new todo")(AddTodoForm(handleFormSubmit))
            ),
          Col
            .xs(23)
            .sm(23)
            .md(21)
            .lg(20)
            .xl(18)(
              Card
                .title("Todo List")(
                  (error, isLoaded) match {
                    case (Some(msg), _) =>
                      Alert
                        .message(s"Error: $msg")
                        .`type`(antdStrings.error)
                        .showIcon(true)
                    case (_, false) =>
                      Spin
                        .size(antdStrings.default)
                        .spinning(true)(
                          Alert
                            .message("Loading Todos")
                            .`type`(antdStrings.info)
                            .showIcon(true)
                        )
                    case _ =>
                      TList(todos, handleTodoToggle, handleRemoveTodo)
                  }
                )
            )
        )
  }
}