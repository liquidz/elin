let g:elin#babashka = get(g:, 'elin#babashka', 'bb')

let s:queue = []

function! elin#notify(...) abort
  let conn = elin#server#connection()
  if conn is# v:null
    call add(s:queue, {'type': 'notify', 'request': a:000})
    return
  endif
  return call(function('elin#internal#rpc#notify'), [conn] + a:000)
endfunction

function! elin#request(...) abort
  let conn = elin#server#connection()
  if conn is# v:null
    call add(s:queue, {'type': 'request', 'request': a:000})
    return
  endif
  return call(function('elin#internal#rpc#request'), [conn] + a:000)
endfunction

function! elin#ready() abort
  for q in s:queue
    if q.type ==# 'notify'
      call call('elin#notify', q.request)
    elseif q.type ==# 'request'
      call call('elin#request', q.request)
    endif
  endfor
  let s:queue = []
endfunction

function! elin#intercept_notify(...) abort
  if elin#server#connection() is# v:null
    return v:null
  endif
  return elin#notify('elin.handler.internal/intercept', a:000)
endfunction

function! elin#intercept_request(...) abort
  if elin#server#connection() is# v:null
    return v:null
  endif
  return elin#request('elin.handler.internal/intercept', a:000)
endfunction

function! elin#status() abort
  try
    if elin#server#connection() is# v:null
      return ''
    endif
    return elin#request('elin.handler.internal/status', [])
  catch
    return ''
  endtry
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
