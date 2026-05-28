const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const packageJson = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const stageDir = path.join(root, 'build', 'vsix');
const extensionDir = path.join(stageDir, 'extension');
const distributionsDir = path.join(root, 'build', 'distributions');

fs.rmSync(stageDir, { recursive: true, force: true });
fs.mkdirSync(extensionDir, { recursive: true });
fs.mkdirSync(distributionsDir, { recursive: true });

for (const name of ['package.json', 'README.md', 'src', 'media']) {
    fs.cpSync(path.join(root, name), path.join(extensionDir, name), { recursive: true });
}

fs.writeFileSync(path.join(stageDir, '[Content_Types].xml'), `<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="json" ContentType="application/json"/>
    <Default Extension="js" ContentType="application/javascript"/>
    <Default Extension="md" ContentType="text/markdown"/>
    <Default Extension="svg" ContentType="image/svg+xml"/>
    <Default Extension="vsixmanifest" ContentType="text/xml"/>
    <Default Extension="xml" ContentType="text/xml"/>
</Types>
`);

fs.writeFileSync(path.join(stageDir, 'extension.vsixmanifest'), `<?xml version="1.0" encoding="utf-8"?>
<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
    <Metadata>
        <Identity Language="en-US" Id="${escapeXml(packageJson.name)}" Version="${escapeXml(packageJson.version)}" Publisher="${escapeXml(packageJson.publisher)}"/>
        <DisplayName>${escapeXml(packageJson.displayName)}</DisplayName>
        <Description xml:space="preserve">${escapeXml(packageJson.description)}</Description>
        <Categories>Other</Categories>
        <Tags>code,memo,context,ai</Tags>
        <Properties>
            <Property Id="Microsoft.VisualStudio.Code.Engine" Value="${escapeXml(packageJson.engines.vscode)}"/>
            <Property Id="Microsoft.VisualStudio.Code.ExtensionKind" Value="workspace"/>
        </Properties>
    </Metadata>
    <Installation>
        <InstallationTarget Id="Microsoft.VisualStudio.Code"/>
    </Installation>
    <Dependencies/>
    <Assets>
        <Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true"/>
        <Asset Type="Microsoft.VisualStudio.Services.Content.Details" Path="extension/README.md" Addressable="true"/>
        <Asset Type="Microsoft.VisualStudio.Services.Icons.Default" Path="extension/media/memo-activity.svg" Addressable="true"/>
    </Assets>
</PackageManifest>
`);

function escapeXml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('"', '&quot;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;');
}
