(defproject alert-scout "0.1.0-SNAPSHOT"
  :description "Monitoring alerting and reporting system for RSS/Atom feeds"
  :url "https://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [remus "0.2.6"]
                 [clj-http "3.13.1"]
                 [metosin/malli "0.16.4"]]
  :main ^:skip-aot alert-scout.core
  :target-path "target/%s"
  :plugins [[cider/cider-nrepl "0.58.0"]
            [lein-cljfmt "0.9.2"]]
  :repl-options {:init-ns alert-scout.core
                 :prompt (fn [ns] (str "\033[1;34m[" ns "]\033[0m=> "))
                 :init (do
                         (set! *print-length* 100)
                         (set! *print-level* 10)
                         (println "\n╔════════════════════════════════════════╗")
                         (println "║  Alert Scout REPL                       ║")
                         (println "║  Type (help) for common commands        ║")
                         (println "╚════════════════════════════════════════╝\n")
                         (defn help []
                           (println "\nCommon Commands:")
                           (println "  (core/run-once)           - Generate alerts from feeds")
                           (println "  (core/run-once :user-id)  - Generate alerts for specific user")
                           (println "  (require '[...])          - Load a namespace")
                           (println "  (doc symbol)              - Show documentation")
                           (println "  (source symbol)           - Show source code")
                           (println "  (dir namespace)           - List namespace contents\n")))}
  :aliases {"generate-jekyll" ["run" "-m" "alert-scout.core/-generate-jekyll"]
            "lint" ["run" "-m" "clj-kondo.main" "--lint" "src" "test"]
            "check-all" ["do" ["cljfmt" "check"] ["run" "-m" "clj-kondo.main" "--lint" "src" "test"] ["check"]]}
  :profiles {:dev {:dependencies [[nrepl/nrepl "1.3.0"]
                                  [clj-kondo/clj-kondo "2025.12.23"]]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
