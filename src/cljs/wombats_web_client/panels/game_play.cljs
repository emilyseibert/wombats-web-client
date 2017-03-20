(ns wombats-web-client.panels.game-play
  (:require [wombats-web-client.components.arena :as arena]
            [wombats-web-client.components.cards.game
             :refer [open-join-game-modal-fn]]
            [wombats-web-client.components.chat-box :refer [chat-box]]
            [wombats-web-client.components.countdown-timer
             :refer [countdown-timer]]
            [wombats-web-client.components.game-ranking :refer [ranking-box]]
            [wombats-web-client.components.join-button :refer [join-button]]
            [wombats-web-client.components.modals.winner-modal
             :refer [winner-modal]]
            [wombats-web-client.utils.socket :as ws]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

(defonce root-class "game-play-panel")
(defonce canvas-id "arena-canvas")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- resize-canvas [arena-atom]
  (let [root-element (first
                      (array-seq
                       (.getElementsByClassName
                        js/document
                        root-class)))
        canvas-element (.getElementById js/document canvas-id)
        half-width (/ (.-offsetWidth root-element) 2)
        height (.-offsetHeight root-element)
        dimension (min height half-width)]

    (arena/arena @arena-atom canvas-id)
    (set! (.-width canvas-element) dimension)
    (set! (.-height canvas-element) dimension)))

(defn- on-resize [arena-atom]
  (resize-canvas arena-atom)
  (js/setTimeout #(resize-canvas arena-atom)
                 100))

(defn- show-winner-modal
  [winner]
  (re-frame/dispatch [:set-modal {:fn #(winner-modal winner)
                                  :show-overlay true}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Lifecycle Methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- component-did-mount [arena cmpnt-state]
  ;; Add resize listener
  (.addEventListener js/window
                     "resize"
                     (:resize-fn @cmpnt-state))
  (resize-canvas arena))

(defn- component-did-update [arena]
  (arena/arena @arena canvas-id))

(defn- component-will-mount [game-id]
  (ws/send-message :join-game {:game-id game-id}))

(defn- component-will-unmount [game-id cmpnt-state]
  (re-frame/dispatch [:game/update-frame nil])
  (re-frame/dispatch [:game/clear-chat-messages])
  (ws/send-message :leave-game {:game-id game-id})
  (re-frame/dispatch [:set-modal nil])

  ;; Remove resize listener
  (.removeEventListener js/window
                        "resize"
                        (:resize-fn @cmpnt-state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Render Methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- game-play-title [game show-join-button]
  (let [{:keys [:game/id
                :game/is-private
                :game/frame
                :game/start-time
                :game/status]} @game
        round-start-time (:frame/round-start-time frame)
        round-number (:frame/round-number frame)]

    [:div.game-play-title-container

     [:h1.game-play-title
      (case status
        :closed
        "GAME OVER"

        (:pending-open
         :pending-closed
         :active-intermission)
        [:span (str "ROUND " round-number " STARTS IN: ")
         [countdown-timer round-start-time]]

        :active
        (str "ROUND " round-number)

        nil)]

     (when (and show-join-button (= status :pending-open))
       [join-button {:is-private is-private
                     :on-click (open-join-game-modal-fn id)}])]))

(defn- right-game-play-panel
  [game messages user]
  (let [{:keys [:game/name
                :game/winner
                :game/players
                :game/max-players]} @game
        in-game (pos? (count (filter #(= (get-in % [:player/user :user/github-username])
                                         (:user/github-username @user))
                                     (vals players))))]

    ;; Dispatch winner modal if there's a winner
    (when winner
      (show-winner-modal winner))

    [:div.right-game-play-panel

     [:div.top-panel
      [game-play-title game (not in-game)]
      [:h2.game-play-subtitle (str name " - High Score")]
      [:p.wombat-counter
       (str "Wombats: " (count players) "/" max-players)]
      [ranking-box game]]

     (when in-game
       [:div.chat-panel
        [:div.chat-title [:span "CHAT"]]
        [chat-box game messages]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main Method
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn game-play [{:keys [game-id]}]
  (let [arena (re-frame/subscribe [:game/arena])
        cmpnt-state (reagent/atom {:resize-fn #(on-resize arena)})
        messages (re-frame/subscribe [:game/messages])
        user (re-frame/subscribe [:current-user])
        game (re-frame/subscribe [:games/game-by-id game-id])]

    (reagent/create-class
     {:component-did-mount #(component-did-mount arena cmpnt-state)
      :component-did-update #(component-did-update arena)
      :component-will-mount #(component-will-mount game-id)
      :component-will-unmount #(component-will-unmount game-id cmpnt-state)
      :display-name "game-play-panel"
      :reagent-render
      (fn []
        (let [winner (:game/winner @game)]

          (arena/arena @arena canvas-id)
          [:div {:class-name root-class}
           [:div.left-game-play-panel {:id "wombat-arena"
                                       :class (when winner "game-over")}
            [:canvas {:id canvas-id}]]
           [right-game-play-panel game messages user]]))})))
