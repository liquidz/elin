function! elin#internal#rpc#connect(addr, ...) abort
  let options = get(a:, 1, {})
  return s:connect(a:addr, options)
endfunction

function! elin#internal#rpc#disconnect(conn) abort
  return s:disconnect(a:conn)
endfunction

function! elin#internal#rpc#request(conn, method, params, ...) abort
  let options = get(a:, 1, {})
  if has_key(options, 'callback')
    echoerr 'Callback is not supported in request'
    return
  endif

  try
    return s:request(a:conn, a:method, [a:params] + [options])
  catch
    call elin#internal#echom(printf('Elin failed to request: %s', v:exception), 'ErrorMsg')
  endtry
endfunction

function! elin#internal#rpc#notify(conn, method, params, ...) abort
  let options = get(a:, 1, {})
  let Callback = get(options, 'callback', v:null)
  if Callback isnot v:null
    let callback_id = Callback is v:null ? v:null : elin#callback#register(Callback)
    let options['callback'] = callback_id
  endif

  try
    return s:notify(a:conn, a:method, [a:params] + [options])
  catch
    call elin#internal#echom(printf('Elin failed to notify: %s', v:exception), 'ErrorMsg')
  endtry
endfunction

if has('nvim')

  function! s:connect(addr, options) abort
    let options = extend({
          \   'on_close': { -> 0 },
          \ }, get(a:, 1, {}))
    let id = sockconnect('tcp', a:addr, {'rpc': v:true})
    if id is# 0
      throw printf('Failed to connect: %s', a:addr)
    endif
    let conn = {
          \ 'id': id,
          \ 'on_close': options.on_close,
          \}
    return conn
  endfunction

  function! s:disconnect(conn) abort
    call sockclose(a:conn.id)
    call a:conn.on_close(a:conn)
  endfunction

  function! s:request(conn, method, params) abort
    return call('rpcrequest', [a:conn.id, a:method] + a:params)
  endfunction

  function! s:notify(conn, method, params) abort
    return call('rpcnotify', [a:conn.id, a:method] + a:params)
  endfunction

else

  function! s:fixme(ch, msg) abort
    echom printf('CHANNEL CALLBACK: %s', a:msg)
  endfunction

  function! s:connect(addr, options) abort
    let options = extend({
          \   'on_close': { -> 0 },
          \ }, get(a:, 1, {}))

    let ch = ch_open(a:addr, {
          \ 'mode': 'json',
          \ 'callback': funcref('s:fixme'),
          \ 'drop': 'auto',
          \ 'noblock': 1,
          \ 'timeout': 1000 * 60 * 60 * 24 * 7,
          \ 'close_cb': options.on_close,
          \})
    if ch_status(ch) !=# 'open'
      throw printf('Failed to connect: %s', a:addr)
    endif
    return ch
  endfunction

  function! s:disconnect(ch) abort
    return ch_close(a:ch)
  endfunction

  function! s:request(ch, method, params) abort
    let [error, result] = ch_evalexpr(a:ch, [a:method] + a:params)
    if error isnot# v:null
      throw error
    endif
    return result
  endfunction

  function! s:notify(ch, method, params) abort
    return ch_sendraw(a:ch, json_encode([0, [a:method] + a:params]) . "\n")
  endfunction

endif
