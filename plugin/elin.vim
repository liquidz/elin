if exists('g:loaded_vim_elin')
  finish
endif
let g:loaded_vim_elin = 1

let g:elin_home = expand('<sfile>:p:h:h')
let g:elin_auto_connect = get(g:, 'elin_auto_connect', v:true)
let g:elin_server_port = get(g:, 'elin_server_port', v:null)

function! s:init() abort
  if g:elin_server_port is v:null
    echom 'vim-elin: start server'
    call elin#server#start()
  endif

  if g:elin_auto_connect is v:true
    echom 'vim-elin: connect to server'
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
    au VimEnter *.clj,*.cljs,*.cljc,*.cljd call s:init()
  aug END
else
  if &ft ==# 'clojure'
    call s:init()
  endif
endif
