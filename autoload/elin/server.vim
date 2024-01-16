let s:job = v:null
let s:port = v:null
let s:conn = v:null
let s:host = has('nvim') ? 'nvim' : 'vim'

function! elin#server#start() abort
  call elin#script#empty_port(funcref('s:start'))
  call timer_start(100, funcref('s:try_connecting'), {'repeat': -1})
endfunction

function! s:try_connecting(timer_id) abort
  echom printf('try to connect: %s', s:port)
  if s:port is# v:null
    return
  endif

  try

    let s:conn = elin#compat#rpc#connect(printf('localhost:%s', s:port))
    echom 'connected'
    return timer_stop(a:timer_id)
  catch
    echom printf('failed to connect: %s', v:exception)
    return v:null
  endtry
endfunction

function! elin#server#stop() abort
  return s:stop()
endfunction

function! elin#server#connection() abort
  return s:conn
endfunction

function! s:start(port) abort
  let s:port = a:port
  let command = [g:elin#babashka, '-m', 'elin.core', s:host, a:port, getcwd()]
  let options = {
        \ 'cwd': g:elin_home,
        \ }
  let s:job = elin#compat#job#start(command, options)
endfunction

function! s:stop() abort
  if s:job is# v:null
    return
  endif

  call elin#compat#job#stop(s:job)
  let s:job = v:null
endfunction
