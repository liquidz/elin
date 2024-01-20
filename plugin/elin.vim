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
endfunction

function! s:deinit() abort
  call elin#server#disconnect()
  call elin#server#stop()
endfunction

if has('vim_starting')
  aug elin_starting_group
    au!
    au VimEnter * call s:init()
    au VimLeave * call s:deinit()
  aug END
else
  call s:init()
endif
