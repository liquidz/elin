function! elin#internal#rpc#connect(addr, ...) abort
  let options = get(a:, 1, {})
  return s:connect(a:addr, options)
endfunction

function! elin#internal#rpc#disconnect(conn) abort
  return s:disconnect(a:conn)
endfunction

function! elin#internal#rpc#request(conn, method, params, ...) abort
  let options = get(a:, 1, {})
  try
    return s:request(a:conn, a:method, [a:params] + [options])
  catch
    call elin#internal#echom(printf('Elin failed to request: %s', v:exception), 'ErrorMsg')
  endtry
endfunction

function! elin#internal#rpc#notify(conn, method, params, ...) abort
  let options = get(a:, 1, {})
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
          \ }, a:options)
    let id = sockconnect('tcp', a:addr, {'rpc': v:true})
    if id is# 0
      throw printf('Failed to connect: %s', a:addr)
    endif
    let conn = {
          \ 'id': id,
          \ 'on_close': options.on_close,
          \}
    let conn.healthcheck_timer = timer_start(500, funcref('s:healthcheck', [conn]), {'repeat': -1})
    return conn
  endfunction

  function! s:healthcheck(conn, timer_id) abort
    try
      call rpcnotify(a:conn.id, 'elin.handler.internal/healthcheck')
    catch
      call timer_stop(a:conn.healthcheck_timer)
      call a:conn.on_close(a:conn)
    endtry
  endfunction

  function! s:disconnect(conn) abort
    call timer_stop(a:conn.healthcheck_timer)
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

  function! s:connect(addr, options) abort
    let options = extend({
          \   'on_close': { -> 0 },
          \ }, a:options)

    let ch = ch_open(a:addr, {
          \ 'mode': 'json',
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
