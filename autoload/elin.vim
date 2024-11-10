let g:elin#babashka = get(g:, 'elin#babashka', 'bb')
let g:elin#status_text = ''

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

function! elin#request_async(handler_name, args, callback) abort
  let id = elin#callback#register(a:callback)
  let config = printf('{:interceptor {:uses [elin.interceptor.handler/callback {:id "%s"}]}}', id)
  return elin#notify(a:handler_name, a:args, {'config': config})
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
  if !elin#server#is_connected()
    return v:null
  endif
  return elin#notify('elin.handler.internal/intercept', a:000)
endfunction

function! elin#intercept_request(...) abort
  if !elin#server#is_connected()
    return v:null
  endif
  return elin#request('elin.handler.internal/intercept', a:000)
endfunction

function! s:update_status_text(resp) abort
  let g:elin#status_text = a:resp
endfunction

function! elin#status() abort
  try
    if !elin#server#is_connected()
      return ''
    endif

    call elin#request_async('elin.handler.internal/status', [], funcref('s:update_status_text'))
    return g:elin#status_text
  catch
    return ''
  endtry
endfunction
