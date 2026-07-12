(ns reception.advisor
  "ReceptionAdvisor — proposes a reception operation (schedule an
  appointment, take a message, send a confirmation) for a registered
  business. Swappable: `mock-advisor` (deterministic, default) or
  `llm-advisor`. Either way the advisor ONLY produces a PROPOSAL; the
  governor checks slot collisions against the committed calendar
  independently. Modeled on cloud-itonami-isco-4311's
  bookkeeping.advisor.

  A proposal is a map:
    {:op :schedule-appointment|:take-message|:send-confirmation
     :effect :propose
     :slot {:resource str :start int :end int}   ; minutes-of-day
     :party str
     :message str
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}"
  )

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  [_store {:keys [op stake slot party message] :as request}]
  {:op op
   :effect :propose
   :slot slot
   :party party
   :message message
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a reception advisor. Given an operation request, propose an
   :op, the :slot ({:resource :start :end}, minutes-of-day integers),
   the :party, an honest :confidence and a :stake. Never claim a slot
   is free — the governor checks the calendar.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
