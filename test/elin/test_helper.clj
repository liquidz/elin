(ns elin.test-helper
  (:require
   [malli.dev.pretty :as m.d.pretty]
   [malli.instrument :as m.inst]))

(m.inst/instrument!
 {:report (m.d.pretty/reporter)})
