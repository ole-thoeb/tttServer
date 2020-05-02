module MaterialUI.Internal.Component exposing (Index, Indexed, getSet, update, GetSet, elementCss, delayedCmd, cmd, subscriptions)


import Dict exposing (Dict)
import Element
import Html.Attributes as Attributes
import Process
import Task


type alias Index = String


type alias Indexed a = Dict String a


type alias GetSet store model =
    { get : Index -> store -> model
    , set : Index -> model -> store -> store
    }

getSet : (store -> Indexed model)
    -> (Indexed model -> store -> store)
    -> model
    -> GetSet store model
getSet getModel setModel default =
    { get = \index store -> Dict.get index (getModel store) |> Maybe.withDefault default
    , set = \index model store -> setModel (Dict.insert index model (getModel store)) store
    }


update : GetSet store model
    -> (mIn -> mOut)
    -> (mIn -> model -> ( model, Cmd mIn ))
    -> mIn
    -> Index
    -> store
    -> ( store, Cmd mOut )
update get_set lift update_ msg index store =
    let
        model = get_set.get index store
        ( updatedModel, effects ) = update_ msg model
    in
    ( get_set.set index updatedModel store, Cmd.map lift effects )


subscriptions : ({ store | lift : mOut -> msg } -> Indexed model)
    -> (Index -> mIn -> mOut)
    -> { store | lift : mOut -> msg }
    -> (model -> Sub mIn)
    -> Sub msg
subscriptions get lift store sub =
    let
        lift_ index = store.lift << lift index
    in
    Dict.foldr (\index model acc ->  Sub.map (lift_ index) (sub model) :: acc) [] (get store)
        |> Sub.batch



elementCss : String -> String -> Element.Attribute msg
elementCss property value =
    Element.htmlAttribute <| Attributes.style property value


delayedCmd : Float -> msg -> Cmd msg
delayedCmd delay command =
    Task.perform (always command) <| Process.sleep delay


cmd : msg -> Cmd msg
cmd msg =
    Task.perform identity <| Task.succeed msg