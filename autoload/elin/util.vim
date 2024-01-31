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
