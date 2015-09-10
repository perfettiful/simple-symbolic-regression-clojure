(ns simple-symbolic-regression-clojure.gp-test
  (:use midje.sweet)
  (:use [simple-symbolic-regression-clojure.gp]
        [simple-symbolic-regression-clojure.core])
  )

;; helpers for testing

(defn cycler
  [returns]
  (atom (cycle returns))
  )

(defn step-cycler
  [c]
  (let [result (first @c)]
    (do
      (swap! c rest)
      result
  )))

;; a bit of exploration on how to test stochastic functions

(fact "we can call step-cycler to produce a number from the cycler"
  (let [cc (cycler [-1 2 -3 4])]
    (step-cycler cc) => -1
    (step-cycler cc) => 2
    (step-cycler cc) => -3
    (step-cycler cc) => 4
    ))

(fact "we can thus stub random number generation with a cycler"
  (let [stubby (cycler [8 7 2 5])]
    (with-redefs [rand-int (fn [arg] (step-cycler stubby))]
      (take 7 (repeatedly #(rand-int 1000))) => [8 7 2 5 8 7 2]))
  )

(fact "it works for other random things too"
  (let [stubby (cycler [0.8 0.7 0.2 0.5])]
    (with-redefs [rand (fn [arg] (step-cycler stubby))]
      (take 7 (repeatedly #(rand 9.0))) => [0.8 0.7 0.2 0.5 0.8 0.7 0.2]))
  )

;; random token

(fact "random-token produces a random token, including ERCs if told to"
    (random-token [1]) => 1
    (into #{} (repeatedly 1000 #(random-token [1 2 3 4]))) => #{1 2 3 4}
    (into #{} (repeatedly 1000 #(random-token ['(rand-int 5)]))) => #{0 1 2 3 4}
    (into #{} (repeatedly 1000 #(random-token [:x :+ '(rand-int 5)]))) => #{0 1 2 3 4 :x :+}
  )

(fact "random-token returns nil when passed an empty collection"
    (random-token []) => nil
  )

;; random script

(fact "random-script creates a vector of a given number of calls to random-tokens"
  (let [stubby (cycler [9 :x])]
    (with-redefs [random-token (fn [args] (step-cycler stubby))]
      (random-script [1 2 3 4 5] 8) => [9 :x 9 :x 9 :x 9 :x]
  )))

;; uniform crossover

(fact "uniform crossover takes two collections and picks the value at each position with equal probability from each parent"
  (let [stubby (cycler [0.0 1.0])] ;; start with mom, alternate with dad
    (with-redefs [rand (fn [] (step-cycler stubby))]
      (uniform-crossover [1 1 1 1 1 1 1 1 1 1] [2 2 2 2 2 2 2 2 2]) => [2 1 2 1 2 1 2 1 2]))
  )

(fact "uniform crossover works with different-length collections"
  (count (uniform-crossover [1 1 1 1 1] [2 2 2 2 2 2 2 2 2])) => 5
  (count (uniform-crossover [1 1 1 1 1] [2])) => 1
  )

(fact "uniform crossover works with empty collections"
  (count (uniform-crossover [1 1 1 1 1] [])) => 0
  (count (uniform-crossover [] [1 1 1 1 1])) => 0
  )

;; uniform mutation

(fact "uniform mutation takes a collection, and changes each position to a new sampled value with specified probability"
    (uniform-mutation [1 1 1] [4] 1.0) => [4 4 4]
    (uniform-mutation [1 1 1] [4] 0.0) => [1 1 1]
    (into #{} (uniform-mutation [1 1 1 1 1 1 1 1 1 1] [4 5] 1.0)) => #{4 5}
    (into #{} (uniform-mutation [1 1 1 1 1 1 1 1 1 1] ['(+ 9 (rand-int 2))] 1.0)) => #{9 10}
  )

(fact "uniform mutation works with empty collections"
    (uniform-mutation [] [4] 1.0) => []
    (uniform-mutation [1 1 1 1] [] 1.0) => [1 1 1 1]
    )

;; uneven one-point-crossover

(fact "one-point crossover takes two collections and splices them together at a randomly chosen internal point"
  (let [stubby (cycler [3 3 2 4 1 5 7 0 0 7])] ;; these will be used in pairs to pick slice points
    (with-redefs [rand-int (fn [arg] (step-cycler stubby))]
      (one-point-crossover [1 1 1 1 1 1 1 1] [2 2 2 2 2 2 2 2]) => [1 1 1 2 2 2 2 2]
      (one-point-crossover [1 1 1 1 1 1 1 1] [2 2 2 2 2 2 2 2]) => [1 1 2 2 2 2]
      (one-point-crossover [1 1 1 1 1 1 1 1] [2 2 2 2 2 2 2 2]) => [1 2 2 2]
      (one-point-crossover [1 1 1 1 1 1 1 1] [2 2 2 2 2 2 2 2]) => [1 1 1 1 1 1 1 2 2 2 2 2 2 2 2]
      (one-point-crossover [1 1 1 1 1 1 1 1] [2 2 2 2 2 2 2 2]) => [2]
      ))
  )

;; Individuals

(facts "can construct an Individual and access its script and score"
       (fact "works with non-nil scores"
             (let [script [:x :y :+]
                   individual (make-individual script 12)]
               (:script individual) => script
               (:score individual) => 12))
       (fact "works with nil score"
             (let [script [:x :y :+]
                   individual (make-individual script nil)]
               (:script individual) => script
               (:score individual) => nil))
       (fact "works with no score given"
             (let [script [:x :y :+]
                   individual (make-individual script)]
               (:script individual) => script
               (:score individual) => nil))
       )

(fact "can set the score of an individual"
      (let [script [:x :y +]
            score 27
            individual (make-individual script)
            scored-individual (set-score individual score)]
        (:script individual) => script
        (:score individual) => nil
        (:script scored-individual) => script
        (:score scored-individual) => score
        ))

(fact "can create a random individual (unscored)"
      (into #{} (:script (random-individual ['(rand-int 7) :x :+ :- :* :÷] 1000))) =>
        #{0 1 2 3 4 5 6 :x :+ :- :* :÷}
      )

;; selection

(fact "given a set of Individuals, with unique scores, I can return the lowest-scoring one"
  (let [dudes [(make-individual [1] 12) 
               (make-individual [2] 2)
               (make-individual [2] 1)]]
    (count (winners dudes)) => 1
    (:score (first (winners dudes))) => 1
  ))

(fact "given a set of Individuals, I can return all the lowest-scoring ones"
  (let [dudes [(make-individual [1] 1) 
               (make-individual [2] 200)
               (make-individual [3] 1)
               (make-individual [4] 1)]]
    (count (winners dudes)) => 3
    (sort (map :script (winners dudes))) => [[1] [3] [4]]
  ))

(fact "given a set of Individuals, some without scores, I will not return the scoreless ones"
  (let [dudes [(make-individual [1] nil) 
               (make-individual [2] 2)
               (make-individual [2] 1)]]
    (count (winners dudes)) => 1
    (:score (first (winners dudes))) => 1
  ))

(fact "given a set of Individuals, none with scores, I will return an empty list"
  (let [dudes [(make-individual [1] nil) 
               (make-individual [2] nil)
               (make-individual [2] nil)]]
    (count (winners dudes)) => 0
  ))


;; Populations & Search

;; y=sin(x)
;; population of ~1000
;; training cases: ~100
;; initial size: ~20
;; mutation + crossover


(fact "we can calculate a sin(x)"
  (Math/sin 0.0) => 0.0
  (Math/sin (/ Math/PI 2)) => 1.0
  (Math/sin Math/PI) => (roughly 0.0 0.001))


(defn random-population
  [pop-size constructor-fn]
  (repeatedly pop-size constructor-fn))


(def token-generator
  ['(rand-int 100) :x :+ :- :* :÷])


(fact "we can make a population of random individuals"
  (let [random-dude (fn [] (random-individual token-generator 20))]
    (count (random-population 100 random-dude)) => 100))


(def sine-rubrics
  (repeatedly 10 
    #(let [x (rand (* 2 Math/PI))]
      (->Rubric {:x x} (Math/sin x)))))


(fact "sine-rubrics contain the numerical values I imagine they should"
  (count sine-rubrics) => 10
  (map #(Math/sin (get-in % [:input :x])) sine-rubrics) => (map :output sine-rubrics))


(fact "we can score an Individual with a Rubric (or set)"
  (let [random-dude (fn [] (random-individual token-generator 20))]
    (:score (random-dude)) => nil
    (> (:score (score-using-rubrics (random-dude) sine-rubrics)) 0) => true
    ))


(defn score-population
  "takes an unscored population and returns the same ones with scores assigned"
  [population rubrics]
  (map #(score-using-rubrics % rubrics) population))


(fact "we can score a whole population with `score-population`"
  (let [random-dude (fn [] (random-individual token-generator 20))]
    (not-any? nil? 
      (map 
        :score 
        (score-population (random-population 100 random-dude)
          sine-rubrics)))) => true)


(defn make-baby
  "creates a new scored Individual by sampling a population (with uniform probability) and applying one-pt crossover and mutation, then scoring with the rubrics"
  [population mutation-rate rubrics]
  (let [mom (:script (rand-nth population))
        dad (:script (rand-nth population))
        baby-script (uniform-mutation
                      (one-point-crossover mom dad)
                      token-generator
                      mutation-rate)]
    (make-individual baby-script (total-score-on baby-script rubrics))))


(fact "`make-baby` produces a new scored Individual"
  (let [random-dude (fn [] (random-individual token-generator 100))
        population (score-population
                    (random-population 10 random-dude)
                    sine-rubrics)]
    (> (count (:script (make-baby population 0.01 sine-rubrics))) 0)  => true
    (nil? (:score (make-baby population 0.01 sine-rubrics))) => false
  ))


(defn one-seasonal-cycle
  "doubles the population size by calling `make-baby`, sorts the entire population by score (worse is bigger), removes the worst-scoring ones"
  [population mutation-rate rubrics]
  (let [carrying-capacity (count population)
        new-brood (repeatedly
                    carrying-capacity
                    #(make-baby population mutation-rate rubrics))]
    (take carrying-capacity (sort-by :score (concat population new-brood)))
    ))


(def random-sine-guess (fn [] (random-individual token-generator 100)))


(def initial-sine-population
  (score-population (random-population 200 random-sine-guess) sine-rubrics))


; (println (sort-by :score initial-sine-population))

; (println (sort-by :score (one-seasonal-cycle initial-sine-population 0.01 sine-rubrics)))

(defn future-history
  "creates a lazy list of iterations by applying `one-seasonal-cycle` to an initial population"
  [initial-pop mutation-rate rubrics]
  (iterate #(one-seasonal-cycle % mutation-rate rubrics) initial-pop))


(println (sort-by
            :score
            (nth (future-history initial-sine-population 0.05 sine-rubrics) 50)))
