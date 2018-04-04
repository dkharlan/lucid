(ns lucid.queries)

(def nearby-players-with-target
  '[:find ?online-player-name 
    :in $ ?speaker-name [?online-player-name ...]
    :where [?speaker :character/name ?speaker-name]
           [?speaker :character/body ?speaker-body]
           [?speaker-body :body/location ?speaker-location]
           [?other-player-body :body/location ?speaker-location]
           [?other-player :character/body ?other-player-body]
           [?other-player :character/name ?other-player-name]
    [(= ?online-player-name ?other-player-name)]])

(def nearby-players-without-target
  '[:find ?logged-in-cn
    :in $ ?cn [?logged-in-cn ...]
    :where [?c :character/name ?cn]
           [?c :character/body ?cb]
           [?cb :body/location ?r]
           [?b :body/location ?r]
           [?logged-in-c :character/body ?b]
           [?logged-in-c :character/name ?logged-in-cn]
           [(!= ?b ?cb)]])

