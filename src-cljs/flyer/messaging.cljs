(ns flyer.messaging
  (:require [flyer.traversal :as traversal]
            [goog.events :as events]))

(def default-message 
  "default message structure"
  {:data nil
   :topic "*"
   :channel "*"})

(def default-window 
  "the main window needs to be localized, so that it can be
  referenced"
  js/window)

(defn default-callback 
  "default callback for testing"
  [data topic channel]
  (.log js/console "callback-data:" data)
  (.log js/console "callback-topic:" topic)
  (.log js/console "callback-channel:" channel))

(defn window-post-message
  "performs the window postback"
  ([window msg target]
  (let [data-js (clj->js msg)
        data-json (.stringify js/JSON data-js)
        target-origin (condp = (keyword target)
                        :local (.-origin (.-location window))
                        :all "*"
                        target)]
    ;;TODO: this should include a good origin
    (.postMessage window data-json target-origin)))
  ([window msg] (window-post-message window msg "*")))

(defn broadcast
  "broadcast message to currently active frames"
  [& {:keys [data channel topic target]
      :or {data (:data default-message)
           channel (:channel default-message)
           topic (:topic default-message)
           target :all}}]
  (let [msg {:data data :channel channel :topic topic :target target}
        msg-js (clj->js msg)
        broadcast-list (traversal/generate-broadcast-list)]
    (doseq [window broadcast-list] 
         (window-post-message window msg target))))

(defn create-broadcast-listener
  "used to subscribe to the broadcast messages this takes advantage of
  message postback"
  ([window callback]
     (events/listen
      window (.-MESSAGE events/EventType) callback))
  ([callback] (create-broadcast-listener default-window callback)))

(defn like-this-channel? 
  [msg-channel callback-channel]
  (some true? 
        [(= callback-channel (default-message :channel))
         (= msg-channel callback-channel)]))

(defn like-this-topic?
  [msg-topic callback-topic]
  (some true?
        [(= callback-topic (default-message :topic))
         (= msg-topic callback-topic)
         ;;try and see if it's a regex
         (try 
           (string? (re-matches
                     (re-pattern callback-topic)
                     msg-topic))
           (catch js/Error e
             ;;TODO: include warning when in debug mode
             nil))]))

(defn like-this-origin?
  [msg-origin callback-origin]
  (some true?
        [(= (keyword callback-origin) :all)
         (and (= (keyword callback-origin) :local)
              (= (.-origin (.-location js/window))
                 msg-origin))
         (= msg-origin callback-origin)]))

(defn like-this-flyer?
  "determines if the callback should be called based on the channel
and the topic"
  [msg-topic msg-channel msg-origin
   callback-topic callback-channel callback-origin]
  (every? true? 
          [(like-this-channel? msg-channel callback-channel)
           (like-this-topic? msg-topic callback-topic)
           (like-this-origin? msg-origin callback-origin)]))

(defn subscribe
  "subscribe to broadcast messages"
  [& {:keys [window channel topic callback origin]
      :or {window default-window
           channel (:channel default-message)
           topic (:topic default-message)
           callback default-callback
           origin :all}
      :as sub}]
  (let [callback-wrapper
        (fn [event]
          (let [data (.-data (.getBrowserEvent event))
                msg-js (try (.parse js/JSON data)
                            (catch js/Error e
                              #js {:channel "FOREIGN"
                                   :topic (default-message :topic)
                                   :data data}))
                msg (js->clj msg-js)
                ;;extract data from channel
                msg-channel (or (.-channel msg-js) "FOREIGN")
                msg-topic (or (.-topic msg-js) (default-message :topic))
                msg-data (or (.-data msg-js) (default-message :data))
                msg-origin (.-origin (.getBrowserEvent event))]
            (when (like-this-flyer? msg-topic msg-channel msg-origin
                                    topic channel origin)
              (callback msg-data msg-topic msg-channel msg-origin))))]
    (create-broadcast-listener js/window callback-wrapper)))