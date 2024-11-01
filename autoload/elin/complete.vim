function! elin#complete#omni(findstart, base) abort
  if a:findstart
    let line = getline('.')
    let ncol = col('.')
    let s = line[0:ncol-2]
    return ncol - strlen(matchstr(s, '\k\+$')) - 1
  else
    return elin#request('elin.handler.complete/complete', [a:base])
  endif
endfunction
