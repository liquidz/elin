function! elin#internal#echo(text, highlight) abort
  execute 'echohl' a:highlight
  try
    echo a:text
  finally
    echohl None
  endtry
endfunction

function! elin#internal#echom(text, highlight) abort
  execute 'echohl' a:highlight
	try
    for line in split(a:text, "\n")
      echomsg line
    endfor
	finally
    echohl None
	endtry
endfunction

function! elin#internal#shortening_echo(text) abort
  echo elin#util#shorten(a:text)
endfunction

function! elin#internal#add_curpos_to_jumplist() abort
  " :h jumplist
  " > You can explicitly add a jump by setting the ' mark with "m'".
  silent normal! m'
endfunction

function! elin#internal#jump(path, lnum, col, jump_cmd) abort
  call elin#internal#add_curpos_to_jumplist()
  if expand('%:p') !=# a:path
    execute printf(':keepjumps %s %s', a:jump_cmd, a:path)
  endif
  call cursor(a:lnum, a:col)
  normal! zz
  return v:true
endfunction

function! elin#internal#open_file(path, ...) abort
  let command = get(a:, 1, 'edit')
	let path = a:path
  if !filereadable(a:path)
    let path = input('Open this file?: ', a:path)
	endif

  if empty(path)
    return
  endif

  call mkdir(fnamemodify(path, ':h'), 'p')
  execute printf(':%s %s', command, path)
endfunction

function! elin#internal#eval(str) abort
  return eval(a:str)
endfunction

function! elin#internal#execute(cmd) abort
  call execute(a:cmd)
endfunction

" FIXME WIP ddu only for now
function! elin#internal#select(candidates, callback_handler, ...) abort
  let optional_params = get(a:, 1, [])
  let id = denops#callback#register(
        \ {s -> elin#notify(a:callback_handler, optional_params + [s])},
        \ {'once': v:true},
        \ )
  silent call ddu#start({
        \ 'sources': [{'name': 'custom-list', 'params': {'texts': a:candidates, 'callbackId': id}}],
        \ })
endfunction
