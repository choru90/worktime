"""Emit a review candidate only; never install or execute a server controller."""
import argparse
import ast
import hashlib
from pathlib import Path

from migration_release import require

ORIGINAL_SHA256 = '5ffbf53db65ada79f002f1d825c403870cca047179ea0dd7a57b1b63c126a6ae'
GATE = "service == 'backend' and meta['migration_sha256'] != old['migration_sha256']"


def candidate(source, expected_digest=ORIGINAL_SHA256):
    require(hashlib.sha256(source.encode()).hexdigest() == expected_digest, 'unreviewed controller hash')
    tree = ast.parse(source)
    require(not any(isinstance(n, (ast.Import, ast.ImportFrom)) and 'migration_' in ast.unparse(n)
                    for n in tree.body), 'controller already extended')
    functions = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'deploy']
    require(len(functions) == 1, 'controller deploy shape changed')
    deploy = functions[0]
    require([x.arg for x in deploy.args.args] == ['root', 'service', 'sha', 'incoming'], 'controller signature changed')
    gates = [n for n in ast.walk(deploy) if isinstance(n, ast.If) and ast.unparse(n.test) == GATE]
    require(len(gates) == 1 and len(gates[0].body) == 1 and isinstance(gates[0].body[0], ast.Raise), 'fingerprint guard changed')
    gate = gates[0]
    parents = {child: parent for parent in ast.walk(tree) for child in ast.iter_child_nodes(parent)}
    block = parents[gate]
    require(isinstance(block, ast.With) and len(block.items) == 1
            and ast.unparse(block.items[0].context_expr) == "(root / 'deploy.lock').open('a')"
            and ast.unparse(block.items[0].optional_vars) == 'lock', 'existing deployment lock shape changed')
    flock = [n for n in block.body if isinstance(n, ast.Expr) and ast.unparse(n) == 'fcntl.flock(lock, fcntl.LOCK_EX)']
    require(len(flock) == 1 and block.body.index(flock[0]) < block.body.index(gate), 'exclusive deployment lock missing')
    activations = [n for n in ast.walk(deploy) if isinstance(n, ast.Expr)
                   and ast.unparse(n) == "atomic_json(root / 'manifest.json', new)"]
    require(len(activations) == 1 and isinstance(parents[activations[0]], ast.Try), 'activation contract changed')
    success = parents[activations[0]].body
    require(any(isinstance(n, ast.Expr) and ast.unparse(n) == 'smoke(root)'
                for n in success[:success.index(activations[0])]), 'activation health contract changed')
    block.body.insert(block.body.index(flock[0]) + 1, ast.parse('migration_release.guard(root, service, sha)').body[0])
    block.body.insert(block.body.index(gate), ast.parse('old = migration_release.prepare(root, service, sha, incoming, old)').body[0])
    success.insert(success.index(activations[0]) + 1, ast.parse('migration_release.complete(root, service, sha)').body[0])
    index = 1 if tree.body and isinstance(tree.body[0], ast.Expr) and isinstance(tree.body[0].value, ast.Constant) else 0
    tree.body.insert(index, ast.Import(names=[ast.alias(name='migration_release')]))
    result = ast.unparse(ast.fix_missing_locations(tree)) + '\n'
    require(ast.unparse(gate.test) == GATE, 'fingerprint guard must stay unchanged')
    compile(result, '<review-only-controller-candidate>', 'exec')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--controller', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # Exclusive output: this command cannot overwrite the active controller.
    rendered = candidate(args.controller.read_text())
    with args.output.open('x') as out:
        out.write(rendered)
    print('review_candidate_sha256=' + hashlib.sha256(rendered.encode()).hexdigest())


if __name__ == '__main__':
    main()
