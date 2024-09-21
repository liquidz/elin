function! elin#internal#select#default#run(candidates, callback) abort
  let candidates = map(copy(a:candidates), {i, v -> printf('%d: %s', i + 1, v)})
  let choice = inputlist(['Select:'] + candidates)

  if choice > 0 && choice <= len(a:candidates)
    call a:callback(a:candidates[choice - 1])
  endif
endfunction
