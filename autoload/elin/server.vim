let s:job = v:null
let s:port = v:null
let s:conn = v:null
let s:host = has('nvim') ? 'nvim' : 'vim'

let s:retry_count = 0
let s:retry_interval = 100
let s:retry_max = 20

function! elin#server#start() abort
  call elin#script#empty_port(funcref('s:start'))
  let s:retry_count = 0
  call timer_start(s:retry_interval, funcref('s:try_connecting'), {'repeat': -1})
endfunction

function! elin#server#connect(port) abort
  try
    let s:conn = elin#compat#rpc#connect(printf('localhost:%s', a:port))
    let s:port = a:port
    return v:true
  catch
    return v:false
  endtry
endfunction

function! s:try_connecting(timer_id) abort
  if s:port is# v:null
    return
  endif

  if elin#server#connect(s:port)
    return timer_stop(a:timer_id)
  endif

  let s:retry_count = s:retry_count + 1
  if s:retry_count > s:retry_max
    echom printf('Failed connecting to Elin Server: port=%s, error=%s', s:port, v:exception)
    return timer_stop(a:timer_id)
  endif
endfunction

function! elin#server#stop() abort
  return s:stop()
endfunction

function! elin#server#connection() abort
  return s:conn
endfunction

function! s:start(port) abort
  let s:port = a:port
  let command = [g:elin#babashka, '-m', 'elin.core', s:host, a:port]
  let options = {'cwd': g:elin_home}
  let s:job = elin#compat#job#start(command, options)
endfunction

function! s:stop() abort
  if s:job is# v:null
    return
  endif

  call elin#compat#job#stop(s:job)
  let s:job = v:null
endfunction
