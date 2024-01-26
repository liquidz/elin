let s:job = v:null
let s:port = v:null
let s:conn = v:null
let s:host = has('nvim') ? 'nvim' : 'vim'

let s:retry_count = 0
let s:retry_interval = 100
let s:retry_max = 20

function! elin#server#start(development_mode) abort
  call elin#script#empty_port(funcref('s:start', [a:development_mode]))
endfunction

function! s:start(development_mode, port) abort
  let s:port = a:port
  let command = [g:elin#babashka, '-m', 'elin.core', s:host, a:port, a:development_mode ? 'true' : 'false']
  let options = {'cwd': g:elin_home}
  let s:job = elin#compat#job#start(command, options)
endfunction

function! elin#server#connect(...) abort
  let port = get(a:, 1, v:null)
  let s:retry_count = 0
  call timer_start(s:retry_interval, funcref('s:try_connecting', [port]), {'repeat': -1})
endfunction

function! s:set_script_local(port, connection) abort
  let s:port = a:port
  let s:conn = a:connection
endfunction

function! s:try_connecting(port, timer_id) abort
  let port = a:port is# v:null ? s:port : a:port
  if port is# v:null
    echom 'Port is not set'
    return
  endif

  echom printf("Trying to connect to Elin Server: %s", port)
  if s:connect(port)
    call elin#notify('initialize', [])
    echom printf("Connected to Elin Server: %s", port)
    return timer_stop(a:timer_id)
  endif

  let s:retry_count = s:retry_count + 1
  if s:retry_count > s:retry_max
    echom printf('Failed connecting to Elin Server: port=%s, error=%s', port, v:exception)
    return timer_stop(a:timer_id)
  endif
endfunction

function! s:connect(port) abort
  try
    let s:conn = elin#compat#rpc#connect(printf('localhost:%s', a:port))
    let s:port = a:port
    return v:true
  catch
    return v:false
  endtry
endfunction

function! elin#server#disconnect() abort
  if s:conn is# v:null
    return
  endif

  call elin#compat#rpc#disconnect(s:conn)
  let s:conn = v:null
  let s:port = v:null
endfunction

function! elin#server#stop() abort
  if s:job is# v:null
    return
  endif

  call elin#compat#job#stop(s:job)
  let s:job = v:null
endfunction

function! elin#server#connection() abort
  return s:conn
endfunction
