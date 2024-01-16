if exists('g:loaded_vim_elin')
  finish
endif
let g:loaded_vim_elin = 1

let g:elin_home = expand('<sfile>:p:h:h')

function! s:init() abort
  call elin#server#start()
endfunction

if has('vim_starting')
  aug elin_starting_group
    au!
    au VimEnter * call s:init()
    au VimLeave * call elin#server#stop()

  aug END
else
  call s:init()
endif
