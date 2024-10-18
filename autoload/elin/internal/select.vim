function! elin#internal#select#run(candidates, callback_handler, ...) abort
  let optional_params = get(a:, 1, [])
  return s:select(
        \ a:candidates,
        \ {choice -> elin#notify(a:callback_handler, optional_params + [choice])},
        \ )
endfunction

function! elin#internal#select#_callback(args) abort
  " NOTE
  " Must call elin#callback#call if there is no choices
  " It is required for syncronous request
  if len(a:args) == 1
    call elin#callback#call(a:args[0], v:null)
  else
    call elin#callback#call(a:args[0], a:args[1])
  end
endfunction

if has('nvim')
  function! s:select(candidates, callback ) abort
    let callback_id = elin#callback#register(a:callback)
    call luaeval('require("elin.select").select(_A[1], _A[2], _A[3])', [
         \ a:candidates,
         \ 'elin#internal#select#_callback',
         \ [callback_id],
         \ ])
  endfunction
else
  let g:elin#internal#select#selector = get(g:, 'elin#internal#select#selector', 'default')
  let s:selector = function(printf('elin#internal#select#%s#run', g:elin#internal#select#selector))

  function! s:select(candidates, callback) abort
    call s:selector(a:candidates, a:callback)
  endfunction
endif
