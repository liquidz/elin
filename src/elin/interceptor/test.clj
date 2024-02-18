(ns elin.interceptor.test
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.nrepl.cider.test :as e.f.n.c.test]
   [elin.function.vim.info-buffer :as e.f.v.info-buffer]
   [elin.log :as e.log]
   [elin.util.map :as e.u.map]))

;; function! iced#nrepl#test#done(parsed_response) abort
;;   if empty(a:parsed_response) | return | endif
;;
;;   let errors = a:parsed_response['errors']
;;   let summary = a:parsed_response['summary']
;;   let expected_and_actuals = []
;;   let sign = iced#system#get('sign')
;;
;;   if has_key(a:parsed_response, 'passes')
;;     let passes = a:parsed_response['passes']
;;     for passed_var in uniq(map(copy(passes), {_, v -> v['var']}))
;;       call sign.unplace_by({'group': passed_var})
;;     endfor
;;   else
;;     call sign.unplace_by({'name': s:sign_name, 'group': '*'})
;;   endif
;;
;;   for err in errors
;;     let lnum = ''
;;
;;     if has_key(err, 'lnum')
;;       call sign.place(s:sign_name, err['lnum'], err['filename'], err['var'])
;;       let lnum = printf(' (Line: %s)', err['lnum'])
;;     endif
;;
;;     if has_key(err, 'actual') && !empty(err['actual'])
;;       if has_key(err, 'expected') && !empty(err['expected'])
;;         let expected_and_actuals = expected_and_actuals + []
;;               \ printf(');; %s%s', err['text'], lnum),
;;               \ s:__dict_to_str(err, ['expected', 'actual', 'diffs']),
;;               \ ''
;;       else
;;         let expected_and_actuals = expected_and_actuals + []
;;               \ printf(');; %s%s', err['text'], lnum),
;;               \ err['actual'],
;;               \ ''
;;       endif
;;     endif
;;   endfor
;;
;;   call iced#buffer#error#show(join(expected_and_actuals, "\n"))
;;   call iced#qf#set(errors)
;;
;;   if summary['is_success']
;;     call iced#message#info_str(summary['summary'])
;;   else
;;     call iced#message#error_str(summary['summary'])
;;   endif
;;
;;   call iced#hook#run('test_finished', {})
;;         \ 'result': summary['is_success'] ? 'succeeded' : 'failed',
;;         \ 'summary': summary['summary']
;; endfunction

(def done-test-interceptor
  {:name ::done-test-interceptor
   :kind e.c.interceptor/test
   :leave (fn [{:as ctx :component/keys [host nrepl] :keys [response]}]
            (let [{:keys [passed failed]} (->> (e.f.n.c.test/collect-results nrepl response)
                                               (group-by :result))]
              ;; unsign
              (when (seq passed)
                (e.log/debug "TODO unsign"))

              ;; sign
              (when (seq failed)
                (e.log/debug "TODO sign"))

              (->> failed
                   (mapcat (fn [{:as failed-result :keys [text lnum expected actual]}]
                             (if (empty? actual)
                               []
                               [(format ";; %s%s" text lnum)
                                (if (seq expected)
                                  (e.u.map/map->str failed-result [:expected :actual :diffs])
                                  actual)
                                ""])))
                   (str/join "\n")
                   (e.f.v.info-buffer/append host))
              ;; TODO quickfix
              (comment nil))

            (let [{:keys [succeeded? summary]} (e.f.n.c.test/summary response)]
              (if succeeded?
                (e.log/info host summary)
                (e.log/error host summary)))

            ctx)})
