(ns wombats-web-client.events.in-game
  (:require [re-frame.core :as re-frame]
            [wombats-web-client.utils.socket :as ws]))

(re-frame/reg-event-db
 :game/add-chat-message
 (fn [db [_ message]]
   (update db :game/messages (fn [messages]
                               (conj messages message)))))

(re-frame/reg-event-db
 :game/clear-chat-messages
 (fn [db [_ _]]
   (assoc db :game/messages [])))

(re-frame/reg-event-db
 :game/update-frame
 (fn [db [_ arena]]
   (assoc db :game/arena arena)))

(re-frame/reg-event-db
 :game/info
 (fn [db [_ info]]
   (let [game-id (:game/id info)]
     (assoc-in db
               [:games game-id]
               (merge (get-in db [:games game-id])
                      info)))))

(re-frame/reg-event-db
 :game/join-game
 (fn [db [_ game-id]]
   ;; TODO Add socket connection to bootstrap
   (js/setTimeout
    (fn []
      (ws/send-message :join-game {:game-id game-id})) 2000)
   db))
