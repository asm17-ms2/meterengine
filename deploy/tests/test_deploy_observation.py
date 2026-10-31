import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = '928618307647.dkr.ecr.ap-northeast-2.amazonaws.com'


class ImageObservationTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / 'deploy/observe_images.py'
        self.assertTrue(path.exists(), 'actual image observation is not implemented')
        spec = importlib.util.spec_from_file_location('observe_images', path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.target = {'compose_project':'fixture', 'aws_account_id':'928618307647', 'aws_region':'ap-northeast-2'}
        self.bad = ''

    def docker(self, *args):
        if args[0] == 'ps':
            return 'one\ntwo\n' if self.bad == 'multiple' else ('' if self.bad == 'missing' else 'one\n')
        if args[0] == 'container':
            service = 'frontend' if self.service == 'frontend' else 'backend-green'
            return json.dumps([{'Id':'one', 'Image':'sha256:'+'d'*64, 'Config':{'Image':f'{REGISTRY}/meterengine-backend@sha256:'+'b'*64, 'Labels':{'com.docker.compose.project':'wrong' if self.bad == 'project' else 'fixture','com.docker.compose.service':service}}, 'State':{'Running':self.bad != 'stopped'}}])
        if args[0] == 'image':
            repository = 'frontend' if self.service == 'frontend' else 'backend'
            digest = 'c' if repository == 'frontend' else 'b'
            return json.dumps([{'Id':'sha256:'+('f' if self.bad == 'image-id' else 'd')*64, 'Os':'windows' if self.bad == 'os' else 'linux', 'Architecture':'amd64' if self.bad == 'arch' else 'arm64',
                                'RepoDigests':[] if self.bad == 'digest' else [f'{"wrong.invalid" if self.bad == "repository" else REGISTRY}/meterengine-{repository}@sha256:'+('f' if self.bad == 'wrong-digest' else digest)*64]}])
        self.fail(str(args))

    def observe(self):
        def run(*args):
            if args[0] == 'ps': self.service = args[-1].split('=')[-1]
            return self.docker(*args)
        with patch.object(self.module, 'docker', side_effect=run):
            return self.module.observe(self.target, 'backend-green', 'sha256:'+'b'*64, 'sha256:'+'c'*64)

    def test_observation_uses_container_image_id_and_repo_digest(self):
        result = self.observe()
        self.assertEqual(result['BACKEND_IMAGE_ID'], 'sha256:'+'d'*64)
        self.assertEqual(result['FRONTEND_IMAGE_DIGEST'], 'sha256:'+'c'*64)
        for bad in ['digest','wrong-digest','repository','arch','os','image-id','project','stopped','multiple','missing']:
            with self.subTest(bad=bad):
                self.bad = bad
                with self.assertRaises(ValueError): self.observe()


if __name__ == '__main__':
    unittest.main()
