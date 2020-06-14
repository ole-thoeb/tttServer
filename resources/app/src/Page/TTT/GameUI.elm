module Page.TTT.GameUI exposing (twoPlayerHeader)



import Element exposing (..)
import Element.Border as Border
import Element.Font as Font
import Game.Game as Game
import Game.GamePlayer as GamePlayer
import MaterialUI.Button as Button
import MaterialUI.Theme as Theme exposing (Theme)
import Session
import UIHelper exposing (materialText)



type alias Header msg m o g=
    { leave : msg
    , rematch : msg
    , playerMe : { m | playerRef : GamePlayer.PlayerRef, name : String }
    , opponent : { o | playerRef : GamePlayer.PlayerRef, name : String }
    , game : { g | meTurn : Bool, status : Game.Status }
    }


twoPlayerHeader : Header msg m o g -> Theme Session.CustomColor -> Element msg
twoPlayerHeader header theme =
    case header.game.status of
        Game.OnGoing ->
            row
                [ spaceEvenly
                , width fill
                ]
                [ playerHeader theme header.playerMe header.game.meTurn Left
                , playerHeader theme header.opponent (not header.game.meTurn) Right
                ]

        Game.Draw ->
            row
                [ width fill
                , spacing 8
                ]
                [ materialText
                    [ Font.alignLeft
                    , width <| fillPortion 2
                    ]
                    "Draw"
                    Theme.H3
                    theme
                , headerButtonColumn header theme
                ]

        Game.Win winner _ _ _ ->
            row
                [ width fill
                , spacing 8
                ]
                [ materialText
                    [ Font.alignLeft
                    , width <| fillPortion 2
                    ]
                    (if header.playerMe.playerRef == winner then
                        "Victory"

                     else
                        "Defeat"
                    )
                    Theme.H3
                    theme
                , headerButtonColumn header theme
                ]


headerButtonColumn : Header msg m o g -> Theme a -> Element msg
headerButtonColumn header theme =
    column
        [ width <| fillPortion 1
        , spacing 8
        ]
        [ Button.outlined
            [ width fill
            ]
            { icon = Nothing
            , color = Theme.Primary
            , text = "Rematch"
            , onPress = Just header.rematch
            , disabled = False
            }
            theme
        , Button.outlined
            [ width fill
            ]
            { icon = Nothing
            , color = Theme.Primary
            , text = "Leave"
            , onPress = Just header.leave
            , disabled = False
            }
            theme
        ]


type Alignment
    = Left
    | Right


playerHeader :
    Theme Session.CustomColor
    ->
        { player
            | name : String
            , playerRef : GamePlayer.PlayerRef
        }
    -> Bool
    -> Alignment
    -> Element msg
playerHeader theme player highlight alignment =
    let
        playerColor =
            Theme.Alternative <|
                case player.playerRef of
                    GamePlayer.P1 ->
                        Session.Player1Color

                    GamePlayer.P2 ->
                        Session.Player2Color

        borderColor =
            if highlight then
                Theme.setAlpha 0.6 (Theme.getColor playerColor theme)

            else
                Theme.setAlpha 0.3 (theme.color.onBackground)


        ( fontAlign, align ) =
            case alignment of
                Left ->
                    ( Font.alignLeft, alignLeft )

                Right ->
                    ( Font.alignRight, alignRight )
    in
    el
        [ width shrink
        , Border.color <| borderColor
        , Border.width 2
        , Border.rounded 6
        , padding 8
        ]
    <|
        materialText
            [ fontAlign
            , Font.color theme.color.onBackground
            , align
            ]
            player.name
            Theme.Body1
            theme
