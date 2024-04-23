function! elin#internal#select#fzf#start(candidates, callback_handler, optional_params) abort
  silent call fzf#run(fzf#wrap('elin', {
        \ 'source': a:candidates,
        \ 'options': '--expect=ctrl-t,ctrl-v',
        \ 'sink*': {v -> s:sink(v, a:callback_handler, a:optional_params)},
        \ }))
endfunction

function! s:sink(result, callback_handler, optional_params) abort
  if len(a:result) < 2 | return | endif
  let text = trim(a:result[1])
  call elin#notify(a:callback_handler, a:optional_params + [text])
endfunction
