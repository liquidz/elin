function! elin#internal#select#fzf#run(candidates, callback) abort
  silent call fzf#run(fzf#wrap('elin', {
        \ 'source': a:candidates,
        \ 'options': '--expect=ctrl-t,ctrl-v',
        \ 'sink*': {v -> s:sink(v, a:callback)},
        \ }))
endfunction

function! s:sink(result, callback) abort
  if len(a:result) < 2 | return | endif
  let text = trim(a:result[1])
  call a:callback(text)
endfunction
