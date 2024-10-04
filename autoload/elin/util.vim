function! elin#util#ensure_array(x) abort
  return (type(a:x) == v:t_list ? a:x : [a:x])
endfunction

function! elin#util#select_keys(d, ks) abort
  let ret = {}
  for k in a:ks
    if !has_key(a:d, k) | continue | endif
    let ret[k] = a:d[k]
  endfor
  return ret
endfunction

function! elin#util#cword() abort
  let isk = &iskeyword
  try
    let &iskeyword = '@,48-57,_,192-255,?,-,*,!,+,/,=,<,>,.,:,$,#,%,&,39'
    return expand('<cword>')
  finally
    let &iskeyword = isk
  endtry
endfunction

let s:timer_ids = {}

function! s:start_lazily_callback(id, callback, _) abort
  unlet s:timer_ids[a:id]
  return a:callback()
endfunction

function! elin#util#start_lazily(id, time, callback) abort
  let timer_id = get(s:timer_ids, a:id, -1)
  if timer_id != -1
    call timer_stop(timer_id)
  endif
  let s:timer_ids[a:id] = timer_start(
        \ a:time,
        \ funcref('s:start_lazily_callback', [a:id, a:callback]),
        \ {'repeat': 0},
        \ )
endfunction

function! elin#util#shorten(msg, ...) abort
  let max_length = 0
  if exists('v:echospace')
    let max_length = v:echospace + ((&cmdheight - 1) * &columns)
  else
    let max_length = (&columns * &cmdheight) - 1
    " from experimenting: seems to use 12 characters
    if &showcmd
      let max_length -= 12
    endif

    " from experimenting
    if &laststatus != 2
      let max_length -= 25
    endif
  endif

  let max_length = min([max_length, get(a:, 1, max_length)])
  let msg = substitute(a:msg, '\r\?\n', ' ', 'g')
  return (max_length >= 3 && len(msg) > max_length)
        \ ? strpart(msg, 0, max_length - 3).'...'
        \ : msg
endfunction
