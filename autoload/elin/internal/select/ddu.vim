function! elin#internal#select#ddu#start(candidates, callback_handler, optional_params abort
  let id = denops#callback#register(
        \ {s -> elin#notify(a:callback_handler, a:optional_params + [s])},
        \ {'once': v:true},
        \ )
  silent call ddu#start({
        \ 'sources': [{'name': 'custom-list', 'params': {'texts': a:candidates, 'callbackId': id}}],
        \ })
endfunction
