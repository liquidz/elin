let g:elin#internal#nrepl#reload_before_fn = get(g:, 'elin#internal#nrepl#reload_before_fn', '')
let g:elin#internal#nrepl#reload_after_fn = get(g:, 'elin#internal#nrepl#reload_after_fn', '')

function! s:__gen_reload_options() abort
  let options = {}

  if ! empty(g:elin#internal#nrepl#reload_before_fn)
    let options['before'] = g:elin#internal#nrepl#reload_before_fn
  endif

  if ! empty(g:elin#internal#nrepl#reload_after_fn)
    let options['after'] = g:elin#internal#nrepl#reload_after_fn
  endif

  return options
endfunction

function! elin#internal#nrepl#reload() abort
  let options = s:__gen_reload_options()
  call elin#notify('elin.handler.evaluate/reload', [options])
endfunction

function! elin#internal#nrepl#reload_all() abort
  let options = s:__gen_reload_options()
  call elin#notify('elin.handler.evaluate/reload-all', [options])
endfunction
