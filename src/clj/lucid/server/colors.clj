(ns lucid.server.colors
  (:require [clojure.string :as string]))

(def color-code-start \$)

(def color-codes
  {;; dark red
   \r {:tcp  "\033[31m"
       :http :dark-red}

   ;; light red
   \R {:tcp  "\033[31;1m"
       :http :light-red} 

   ;; dark green
   \g {:tcp  "\033[32m"
       :http :dark-green}

   ;; light green
   \G {:tcp  "\033[32;1m"
       :http :light-green} 

   ;; dark yellow       
   \y {:tcp  "\033[33m"
       :http :dark-yellow}

   ;; light yellow
   \Y {:tcp  "\033[33;1m"
       :http :light-yellow} 

   ;; dark blue
   \b {:tcp  "\033[34m"
       :http :dark-blue}

   ;; light blue
   \B {:tcp  "\033[34;1m"
       :http :light-blue} 

   ;; purple
   \p {:tcp  "\033[35m"
       :http :purple}

   ;; pink
   \P {:tcp  "\033[35;1m"
       :http :pink}

   ;; dark cyan
   \c {:tcp  "\033[36m"
       :http :dark-cyan}

   ;; light cyan
   \C {:tcp  "\033[36;1m"
       :http :light-cyan}

   ;; gray
   \w {:tcp  "\033[37m"
       :http :gray}

   ;; white
   \W {:tcp  "\033[37;1m"
       :http :white}

   ;; reset
   \! {:tcp  "\033[0m"
       :http nil}

   ;; identity
   color-code-start
   {:tcp  (str color-code-start)
    :http :identity}})

