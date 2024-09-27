if exists('g:loaded_vim_elin')
  finish
endif
let g:loaded_vim_elin = 1

let g:elin_home = expand('<sfile>:p:h:h')
let g:elin_auto_connect = get(g:, 'elin_auto_connect', v:true)
let g:elin_server_port = get(g:, 'elin_server_port', v:null)

if !exists('g:elin_default_key_mapping_leader')
  let g:elin_default_key_mapping_leader = '<Leader>'
endif

function! s:init() abort
  if &ft !=# 'clojure' || exists('g:initialized_vim_elin')
    return
  endif
  let g:initialized_vim_elin = 1

  " Initialize internal buffers
  call elin#internal#buffer#info#ready()
  call elin#internal#buffer#temp#ready()

  if g:elin_server_port is v:null
    call elin#server#start()
  endif

  if g:elin_auto_connect is v:true
    call elin#server#connect(g:elin_server_port)
  endif

  " FIXME this should be configurable by .elin.edn
  exe ':sign define elin_error text=🔥 texthl=ErrorMsg'

  aug elin_autocmd_group
    au!
    au BufEnter *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufEnter')
    au BufNewFile *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufNewFile')
    au BufRead *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufRead')
    au BufWritePost *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufWritePost')
    au BufWritePre *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_request('BufWritePre')
    au VimLeave * call s:deinit()
  aug END
endfunction

function! s:deinit() abort
  call elin#intercept_request('VimLeave')
  call elin#server#disconnect()
  if g:elin_server_port is v:null
    call elin#server#stop()
  endif
endfunction

if has('vim_starting')
  aug elin_starting_group
    au!
    au VimEnter * call s:init()
  aug END
else
  if &ft ==# 'clojure'
    call s:init()
  endif
endif

if exists('g:elin_enable_default_key_mappings')
      \ && g:elin_enable_default_key_mappings
  silent! call s:default_key_mappings()
  aug elin_default_key_mappings
    au!
    au FileType clojure call s:default_key_mappings()
  aug END
endif

function! s:define_mapping(map_type, default_keys, plug_name) abort
  if !hasmapto(a:plug_name)
    let keys = substitute(a:default_keys, '<Leader>', g:elin_default_key_mapping_leader, '')
    let cmd = printf('%s <buffer> %s %s',
          \ a:map_type,
          \ keys,
          \ a:plug_name,
          \ )
    call execute(cmd, 'silent!')
  endif
endfunction

function! s:default_key_mappings() abort
  if exists('b:elin_default_key_mappings_applied')
    return
  endif
  let b:elin_default_key_mappings_applied = v:true

  call s:define_mapping('nmap', "<Leader>'",     '<Cmd>call elin#notify("elin.handler.connect/connect", [])<CR>')

  " Evaluation
  call s:define_mapping('nmap', "<Leader>ei",    '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-current-expr", [])<CR>')
  call s:define_mapping('nmap', "<Leader>ee",    '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-current-list", [])<CR>')
  call s:define_mapping('nmap', "<Leader>et",    '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-current-top-list", [])<CR>')
  call s:define_mapping('nmap', "<Leader>eb",    '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-current-buffer", [])<CR>')

  call s:define_mapping('nmap', "<Leader>en",    '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-namespace-form", [])<CR>')
  call s:define_mapping('nmap', "<Leader>ep",    '<Cmd>call elin#notify("elin.handler.evaluate/print-last-result", [])<CR>')
  call s:define_mapping('nmap', "<Leader>ea",    '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-at-mark", [nr2char(getchar())])<CR>')
  call s:define_mapping('nmap', "<Leader>ece",   '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-current-list", [], {"config": "{:interceptor {:includes [elin.interceptor.optional.evaluate/eval-with-context-interceptor]}}"})<CR>')
  call s:define_mapping('nmap', "<Leader>epe",   '<Cmd>call elin#notify("elin.handler.evaluate/evaluate-current-list", [], {"config": "{:interceptor {:includes [[elin.interceptor.optional.evaluate/wrap-eval-code-interceptor \"println\"]]}}"})<CR>')
  call s:define_mapping('nmap', "<Leader>eq",    '<Cmd>call elin#notify("elin.handler.evaluate/interrupt", [])<CR>')
  call s:define_mapping('nmap', "<Leader>eu",    '<Cmd>call elin#notify("elin.handler.evaluate/undef", [])<CR>')
  call s:define_mapping('nmap', "<Leader>eU",    '<Cmd>call elin#notify("elin.handler.evaluate/undef-all", [])<CR>')

  call s:define_mapping('nmap', "<Leader>enr",   '<Cmd>call elin#notify("elin.handler.evaluate/reload", [])<CR>')
  call s:define_mapping('nmap', "<Leader>enR",   '<Cmd>call elin#notify("elin.handler.evaluate/reload-all", [])<CR>')

  " Refactoring
  call s:define_mapping('nmap', "<Leader>ran",   '<Cmd>call elin#notify("elin.handler.namespace/add-libspec", [])<CR>')
  call s:define_mapping('nmap', "<Leader>ram",   '<Cmd>call elin#notify("elin.handler.namespace/add-missing-libspec", [])<CR>')

  " Navigation
  call s:define_mapping('nmap', "<C-]>",         '<Cmd>call elin#notify("elin.handler.navigate/jump-to-definition", [])<CR>')
  call s:define_mapping('nmap', '<Leader>br',    '<Cmd>call elin#notify("elin.handler.navigate/references", [])<CR>')

  " Documentation
  call s:define_mapping('nmap', "K",             '<Cmd>call elin#notify("elin.handler.lookup/lookup", [])<CR>')
  call s:define_mapping('nmap', "<Leader>hs",    '<Cmd>call elin#notify("elin.handler.lookup/show-source", [])<CR>')

  " Testing
  call s:define_mapping('nmap', "<Leader>tt",    '<Cmd>call elin#notify("elin.handler.test/run-test-under-cursor", [])<CR>')
  call s:define_mapping('nmap', "<Leader>tn",    '<Cmd>call elin#notify("elin.handler.test/run-tests-in-ns", [])<CR>')
  call s:define_mapping('nmap', "<Leader>tl",    '<Cmd>call elin#notify("elin.handler.test/rerun-last-tests", [])<CR>')
  call s:define_mapping('nmap', "<Leader>tr",    '<Cmd>call elin#notify("elin.handler.test/rerun-last-tests", [])<CR>')
  call s:define_mapping('nmap', "tt",            '<Cmd>call elin#notify("elin.handler.navigate/cycle-source-and-test", [])<CR>')

  " Misc
  call s:define_mapping('nmap', "<Leader>ss",    '<Cmd>call elin#internal#buffer#info#toggle()<CR>')
  call s:define_mapping('nmap', "<Leader>sl",    '<Cmd>call elin#internal#buffer#info#clear()<CR>')
  call s:define_mapping('nmap', "<Leader><Esc>", '<Cmd>call elin#internal#virtual_text#clear()<CR>')
endfunction
