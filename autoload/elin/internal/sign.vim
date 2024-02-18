function! elin#internal#sign#place(name, lnum, file, group) abort
  try
    return sign_place(0, a:group, a:name, a:file, {'lnum': a:lnum})
  catch /E158:/
    " Invalid buffer name
    execute printf(':edit %s | buffer %d', a:file, bufnr('%'))
    return sign_place(0, a:group, a:name, a:file, {'lnum': a:lnum})
  endtry
endfunction

function! elin#internal#sign#list_in_buffer(...) abort
  let target_buf = get(a:, 1, expand('%:p'))
  let list = sign_getplaced(target_buf, {'group': '*'})
  try
    return list[0]['signs']
  catch
    return []
  endtry
endfunction

function! elin#internal#sign#list_all() abort
  let res = []
  let buffers = filter(range(1, bufnr('$')), {_, i -> bufexists(i)})
  for nr in buffers
    call extend(res, elin#internal#sign#list_in_buffer(nr))
  endfor
  return res
endfunction

function! elin#internal#sign#jump_to_next(...) abort
  let lnum = line('.')
  let opt = get(a:, 1, {})
  let file = get(opt, 'file', expand('%:p'))
  let name = get(opt, 'name', '')
  let sign_list = elin#internal#sign#list_in_buffer(file)
  let target = ''

  if !empty(name)
    call filter(sign_list, {_, v -> v['name'] ==# name})
  endif

  for sign in sign_list
    if sign['lnum'] > lnum
      let target = sign
      break
    endif
  endfor

  if empty(target) && &wrapscan && !empty(sign_list)
    echo 'search hit BOTTOM, continuing at TOP...'
    let target = sign_list[0]
  endif

  if empty(target)
    echo 'Sign is not found.'
  else
    call sign_jump(target['id'], target['group'], '')
  endif
endfunction

function! elin#internal#sign#jump_to_prev(...) abort
  let lnum = line('.')
  let opt = get(a:, 1, {})
  let file = get(opt, 'file', expand('%:p'))
  let name = get(opt, 'name', '')
  let tmp = ''
  let target = ''
  let sign_list = elin#internal#sign#list_in_buffer(file)

  if !empty(name)
    call filter(sign_list, {_, v -> v['name'] ==# name})
  endif

  for sign in sign_list
    if sign['lnum'] < lnum
      let tmp = sign
    elseif sign['lnum'] >= lnum && !empty(tmp)
      let target = tmp
      break
    endif
  endfor

  if empty(target) && &wrapscan && !empty(sign_list)
    echo 'search hit TOP, continuing at BOTTOM...'
    let l = len(sign_list)
    let target = sign_list[l-1]
  endif

  if empty(target)
    echo 'Sign is not found.'
  else
    call sign_jump(target['id'], target['group'], '')
  endif
endfunction

function! elin#internal#sign#unplace_by(opt) abort
  let group = get(a:opt, 'group', '')
  let file = get(a:opt, 'file', '')
  let name = get(a:opt, 'name', '')
  let id = get(a:opt, 'id', '')

  if empty(file)
    let signs = elin#internal#sign#list_all()
  else
    let signs = elin#internal#sign#list_in_buffer(file)
  endif

  if !empty(group) && group !=# '*'
    call filter(signs, {_, v -> v['group'] ==# group})
  endif

  if !empty(id)
    call filter(signs, {_, v -> v['id'] ==# id})
  endif

  if !empty(name)
    call filter(signs, {_, v -> v['name'] ==# name})
  endif

  for sign in signs
    call sign_unplace(sign['group'], {'id': sign['id']})
  endfor
endfunction

function! elin#internal#sign#refresh(...) abort
  let opt = get(a:, 1, {})
  let file = get(opt, 'file', expand('%:p'))
  let signs = get(opt, 'signs', elin#internal#sign#list_in_buffer())

  for sign in signs
    call elin#internal#sign#unplace_by({'id': sign['id'], 'group': sign['group']})
    call elin#internal#sign#place(sign['name'], sign['lnum'], file, sign['group'])
  endfor
endfunction
