let s:job = v:null
let s:port = v:null
let s:conn = v:null
let s:host = has('nvim') ? 'nvim' : 'vim'

let s:retry_count = 0
let s:retry_interval = 100
let s:retry_max = 20

function! elin#server#start() abort
  call elin#script#empty_port(funcref('s:start'))
endfunction

function! s:start(port) abort
  let s:port = a:port
  let config = json_encode({
        \ 'env': {'cwd': expand('%:p:h')},
        \ 'plugin': {'edn-files': elin#internal#plugin#search()},
        \ 'server': {'host': s:host, 'port': str2nr(a:port)},
        \ })
  let command = [g:elin#babashka, '-m', 'elin.core', config]
  let options = {
        \ 'cwd': g:elin_home,
        \ 'err_cb': funcref('s:error_callback'),
        \ }
  let s:job = elin#internal#job#start(command, options)
endfunction

function! s:error_callback(...) abort
  call elin#internal#echom(string(a:000), 'ErrorMsg')
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
    return
  endif

  echo printf("Trying to connect to Elin Server: %s", port)
  if s:connect(port)
    call elin#notify('elin.handler.internal/initialize', [])
    echo printf("Connected to Elin Server: %s", port)
    return timer_stop(a:timer_id)
  endif

  let s:retry_count = s:retry_count + 1
  if s:retry_count > s:retry_max
    call elin#internal#echom(printf('Failed connecting to Elin Server: port=%s, error=%s', port, v:exception), 'ErrorMsg')
    return timer_stop(a:timer_id)
  endif
endfunction

function! s:connect(port) abort
  try
    let s:conn = elin#internal#rpc#connect(
          \ printf('localhost:%s', a:port),
          \ {'on_close': funcref('s:on_close')},
          \ )
    let s:port = a:port
    return v:true
  catch
    return v:false
  endtry
endfunction

function! s:on_close(...) abort
  echom 'Elin server connection is closed'
  if s:port is# v:null
    return
  endif
  call timer_start(100, {_ -> elin#server#connect(s:port)})
endfunction

function! elin#server#disconnect() abort
  if s:conn is# v:null
    return
  endif

  call elin#internal#rpc#disconnect(s:conn)
  let s:conn = v:null
  let s:port = v:null
endfunction

function! elin#server#stop() abort
  if s:job is# v:null
    return
  endif

  call elin#internal#job#stop(s:job)
  let s:job = v:null
endfunction

function! elin#server#connection() abort
  return s:conn
endfunction

function! elin#server#is_connected() abort
  return (elin#server#connection() isnot# v:null)
endfunction
