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
