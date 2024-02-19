let g:elin#babashka = get(g:, 'elin#babashka', 'bb')

function! elin#notify(...) abort
  let conn = elin#server#connection()
  if conn is# v:null
    echom printf('Not connected to Elin server: %s', a:000)
    return
  endif
  return call(function('elin#internal#rpc#notify'), [conn] + a:000)
endfunction

function! elin#request(...) abort
  let conn = elin#server#connection()
  if conn is# v:null
    echom printf('Not connected to Elin server: %s', a:000)
    return
  endif
  return call(function('elin#internal#rpc#request'), [conn] + a:000)
endfunction

function! elin#intercept_notify(...) abort
  return elin#notify('elin.handler.internal/intercept', a:000)
endfunction

function! elin#intercept_request(...) abort
  return elin#request('elin.handler.internal/intercept', a:000)
endfunction

function! s:callback(...) abort
  echom printf('FIXME callback %s', a:000)
endfunction

function! elin#callback_test(method, params) abort
  call elin#notify(a:method, a:params, funcref('s:callback'))
endfunction

function! elin#plus_test(a, b) abort
  return a:a + a:b
endfunction
